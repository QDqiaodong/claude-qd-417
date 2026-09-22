package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.Course;
import com.rink.ice.entity.Enrollment;
import com.rink.ice.entity.IceLane;
import com.rink.ice.entity.Member;
import com.rink.ice.entity.ResurfaceWindow;
import com.rink.ice.repository.CourseRepository;
import com.rink.ice.repository.EnrollmentRepository;
import com.rink.ice.repository.IceLaneRepository;
import com.rink.ice.repository.MemberRepository;
import com.rink.ice.repository.ResurfaceWindowRepository;
import com.rink.ice.util.IceTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EnrollmentService {
    // 报名记录状态：只有「已报」占名额
    private static final String STATUS_ACTIVE = "已报";
    private static final String STATUS_WITHDRAWN = "已退";

    @Autowired
    EnrollmentRepository repo;
    @Autowired
    MemberRepository memberRepo;
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    IceLaneRepository laneRepo;
    @Autowired
    ResurfaceWindowRepository windowRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String today() {
        return LocalDate.now().format(FMT);
    }

    public List<Enrollment> list() {
        return repo.findAll();
    }

    /**
     * 报名：承载会员×课程的多对多关系，并落地所有业务规则。
     * 全程单事务：先对课程行加 FOR UPDATE 行锁，再做校验、落报名单、同步课程已报人数。
     * 两个窗口同时抢同一课程最后一个名额时，行锁把并发串行化——后到的事务阻塞等前一单
     * 提交，随后用当前读重新判定，名额已满 → 整单失败回滚，已报人数绝不超容量；
     * 报名单与已报人数同一事务提交，也不存在"报名成功但人数没涨"的假成功。
     */
    @Transactional
    public Enrollment enroll(Enrollment f) {
        if (f.memberId == null) throw new BizException("会员必填");
        if (f.courseId == null) throw new BizException("课程必填");

        // 先锁课程行：本课程的报名 / 退课 / 改容量全部在这把锁上串行，
        // 锁内读到的容量是最新已提交值（容量改小提交后，下一单立即按新容量拦）
        Course course = courseRepo.findByIdForUpdate(f.courseId)
                .orElseThrow(() -> new BizException("课程不存在"));
        Member member = memberRepo.findById(f.memberId)
                .orElseThrow(() -> new BizException("会员不存在"));

        // 规则：过期会员禁止选课
        if (member.expireDate != null && member.expireDate.compareTo(today()) < 0)
            throw new BizException("会员 " + member.name + " 已过期，禁止选课");

        // 规则：维护中冰面禁排课（课程所在冰面必须开放）
        if (course.laneId != null) {
            IceLane lane = laneRepo.findById(course.laneId).orElse(null);
            if (lane != null && !"开放".equals(lane.status))
                throw new BizException("课程所在冰面 " + lane.code + " 非开放状态，禁止报名");
        }

        // 规则：浇冰窗口压课期间只冻新报名（不清已报、不挪课）。
        // 窗口改到夜里 / 删除后此判定自动放行，原冻住的课可重新加人。
        List<ResurfaceWindow> windows = windowRepo.findAll();
        List<ResurfaceWindow> freezing = IceTime.freezingWindows(
                course.laneId, course.sessionDate, course.startTime, course.endTime, windows);
        if (!freezing.isEmpty()) {
            ResurfaceWindow w = freezing.get(0);
            throw new BizException("课程 " + course.name + " 于 " + w.winDate + " "
                    + w.startTime + "-" + w.endTime + " 浇冰，正在压课，暂不接受新报名");
        }

        // 重复报名与容量判定用同一份加锁当前读：普通 COUNT 走事务快照，
        // 可能读不到另一窗口刚提交的报名；这里拿到的是最新已提交数据
        List<Enrollment> active = repo.findActiveByCourseIdForUpdate(f.courseId);

        // 规则：同会员同课程只能一条有效报名
        if (active.stream().anyMatch(x -> x.memberId.equals(f.memberId)))
            throw new BizException("该会员已报名此课程，不可重复报名");

        // 规则：课程容量满拦（锁内判定，并发下也绝不超容量）
        if (active.size() >= course.capacity)
            throw new BizException("课程 " + course.name + " 名额已满（" + course.capacity + "）");

        Enrollment e = new Enrollment();
        e.memberId = f.memberId;
        e.courseId = f.courseId;
        e.status = STATUS_ACTIVE;
        e.enrollDate = today();
        e = repo.saveAndFlush(e);

        // 已报人数与报名单同一事务提交：同成功同回滚，杜绝假成功
        course.enrolled = active.size() + 1;
        courseRepo.save(course);
        return e;
    }

    /**
     * 退课：每条报名记录只能真正退一次。
     * 与报名共用同一把课程行锁：退课提交后名额立刻释放，下一单报名即可占住；
     * 连点两次退课（含两个窗口同时点）时，第二笔在加锁重读时看到「已退」→ 整单失败，
     * 名额绝不重复释放。已退记录也不能被直接改回「已报」绕过容量检查，重新报名请走报名接口。
     */
    @Transactional
    public Enrollment update(Long id, Enrollment f) {
        if (f.status == null || f.status.isBlank()) throw new BizException("状态必填");
        String target = f.status.trim();
        if (!STATUS_WITHDRAWN.equals(target))
            throw new BizException("报名状态只能改为「已退」；如需恢复请走报名接口重新报名");

        // 先查出所属课程，再按与报名接口相同的顺序加锁：先课程行、后报名行
        Enrollment probe = repo.findById(id).orElseThrow(() -> new BizException("报名记录不存在"));
        Course course = courseRepo.findByIdForUpdate(probe.courseId).orElse(null);

        // 加锁重读报名行（当前读）：并发的另一笔退课提交后，这里读到的已是「已退」
        Enrollment e = repo.findByIdForUpdate(id).orElseThrow(() -> new BizException("报名记录不存在"));
        if (STATUS_WITHDRAWN.equals(e.status))
            throw new BizException("报名记录 #" + e.id + " 已退课，不能重复退课（名额不重复释放）");
        if (!STATUS_ACTIVE.equals(e.status))
            throw new BizException("报名记录 #" + e.id + " 当前状态「" + e.status + "」不可退课");

        e.status = STATUS_WITHDRAWN;
        e = repo.saveAndFlush(e);

        // 名额在同一事务内立刻释放（当前读重算），下一个报名的人马上能报
        if (course != null) {
            course.enrolled = repo.findActiveByCourseIdForUpdate(e.courseId).size();
            courseRepo.save(course);
        }
        return e;
    }
}

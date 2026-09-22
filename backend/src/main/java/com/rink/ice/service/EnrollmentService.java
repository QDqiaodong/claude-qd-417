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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 选课报名服务。报名占名额 / 退课放名额全部在单事务内完成：
 * - 先对课程行加 FOR UPDATE 写锁，再在锁下做容量判定、落报名、回写课程已报人数，
 *   整单同成功同回滚。两个窗口同时报同一课程时，后到的事务在课程锁上排队，
 *   提交后它做的是锁定读（读最新值），最后一个名额只会被一条报名占住，另一单整单失败；
 * - 数据库另有 (course_id, active_member_id) 唯一索引兜底，同会员同课程不可能两条「已报」；
 * - 退课用「UPDATE ... WHERE status='已报'」条件更新收口：只有第一条请求影响行数为 1、
 *   释放名额；对同一条报名记录再点退课（无论点几次、几个窗口）影响行数为 0，名额不重复释放。
 * - 锁顺序统一为「课程行 → 该课程的报名行」，报名、退课都一样，不会互相死锁。
 */
@Service
public class EnrollmentService {
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
    @Autowired
    EntityManager em;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String today() {
        return LocalDate.now().format(FMT);
    }

    public List<Enrollment> list() {
        return repo.findAll();
    }

    /**
     * 报名：承载会员×课程的多对多关系，并落地所有业务规则。
     */
    @Transactional
    public Enrollment enroll(Enrollment f) {
        if (f.memberId == null) throw new BizException("会员必填");
        if (f.courseId == null) throw new BizException("课程必填");

        Member member = memberRepo.findById(f.memberId)
                .orElseThrow(() -> new BizException("会员不存在"));
        Course course = courseRepo.findById(f.courseId)
                .orElseThrow(() -> new BizException("课程不存在"));

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

        // —— 关键区：锁住课程行，同一课程的报名在此串行 ——
        Course locked = courseRepo.findByIdForUpdate(f.courseId)
                .orElseThrow(() -> new BizException("课程不存在"));
        // 上面的校验可能已把该课程以普通快照装入持久化上下文；FOR UPDATE 会加锁但
        // Hibernate 不会回填最新字段，必须显式 refresh 拿到锁下最新的 capacity/enrolled，
        // 否则柜台改容量 / 并发退课刚提交的新值会被本事务的旧快照覆盖（丢失更新）。
        em.refresh(locked, LockModeType.PESSIMISTIC_WRITE);

        // 锁定读拿到该课程当前所有「已报」记录（读最新提交值，不受事务快照影响）：
        // 重复报名判定与容量计数都以它为准，二者必须在同一份锁下视图里计算。
        List<Enrollment> activeRows = repo.findActiveByCourseForUpdate(f.courseId);

        // 规则：同会员同课程只能一条有效报名（唯一索引 uk_enr_active 是数据库级兜底）
        boolean duplicated = activeRows.stream().anyMatch(e -> f.memberId.equals(e.memberId));
        if (duplicated)
            throw new BizException("该会员已报名此课程，不可重复报名");

        // 规则：课程容量满拦。容量被柜台改小后这里立即按新容量拦：
        // enrolled > capacity（压课状态）时新报名一律失败；容量改回去/改大后无需额外操作，
        // 下一次报名在此读到的新 capacity 自然重新放行。
        int active = activeRows.size();
        if (active >= locked.capacity)
            throw new BizException("课程 " + locked.name + " 名额已满（" + locked.capacity + "）");

        Enrollment e = new Enrollment();
        e.memberId = f.memberId;
        e.courseId = f.courseId;
        e.status = Enrollment.STATUS_ACTIVE;
        e.enrollDate = today();
        try {
            e = repo.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            // 并发兜底：极端情况下两笔报名同时穿过判定，唯一索引只放行一条，另一条整单回滚
            throw new BizException("该会员的报名刚被另一个窗口提交，本单整单失败，请勿重复报名");
        }

        // 名额占用与报名记录同事务提交：无条件按报名表真实条数回写计数（bulk update，
        // 不经过脏检查），避免实体带旧快照时 UPDATE course 被静默跳过的「假成功」。
        courseRepo.syncEnrolledCount(f.courseId);
        return e;
    }

    /**
     * 退课收口：把一条「已报」记录变为「已退」并立即释放名额。
     * 对同一条报名记录重复退课（连点按钮、两个窗口同时点）只有第一次真正生效，
     * 后续请求整单失败且名额不重复释放。报名记录没有其他可改状态，拒绝任意改状态
     * （防止把已退单改回已报绕过容量判定）。
     */
    @Transactional
    public Enrollment update(Long id, Enrollment f) {
        Enrollment e = repo.findById(id).orElseThrow(() -> new BizException("报名记录不存在"));

        if (f.status == null || f.status.isBlank())
            throw new BizException("报名状态必填");
        if (!Enrollment.STATUS_WITHDRAWN.equals(f.status))
            throw new BizException("报名记录只支持退课（状态置为「已退」），不支持改为 " + f.status);

        // 友好提示：顺序操作时直接告诉柜员这单已退；并发情况下仍以条件更新的影响行数为准
        if (Enrollment.STATUS_WITHDRAWN.equals(e.status))
            throw new BizException("报名记录 #" + id + " 已退课，不能重复退课（名额不重复释放）");

        // 先锁课程行（与报名相同的锁顺序：课程 → 报名），名额释放与并发报名在此串行。
        // 校验阶段若已加载过该课程，refresh 保证此处持锁并看到最新计数。
        Course locked = courseRepo.findByIdForUpdate(e.courseId)
                .orElseThrow(() -> new BizException("课程不存在"));
        em.refresh(locked, LockModeType.PESSIMISTIC_WRITE);

        // 条件更新收口：WHERE status='已报' 保证两个窗口同时点退课时只有一条影响 1 行
        int changed = repo.withdrawIfActive(id);
        if (changed == 0)
            throw new BizException("报名记录 #" + id + " 已被退课，本次退课无效，名额不重复释放");

        // 只有真正退掉一条（changed=1）才走到这里；按报名表真实条数无条件回写计数，
        // 名额恰好释放一次，不会被扣成负数，也不会被旧快照覆盖。
        courseRepo.syncEnrolledCount(e.courseId);

        // 退课落库以条件更新为准；返回体脱离会话后再标状态，避免 Hibernate 把同一改动
        // 作为第二次冗余 UPDATE 再刷一遍。
        em.detach(e);
        e.status = Enrollment.STATUS_WITHDRAWN;
        return e;
    }
}

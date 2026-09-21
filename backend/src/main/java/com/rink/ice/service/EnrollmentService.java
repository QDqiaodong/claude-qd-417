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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

        // 规则：同会员同课程只能一条有效报名
        if (repo.countActiveByMemberAndCourse(f.memberId, f.courseId) > 0)
            throw new BizException("该会员已报名此课程，不可重复报名");

        // 规则：课程容量满拦
        long active = repo.countActiveByCourseId(f.courseId);
        if (active >= course.capacity)
            throw new BizException("课程 " + course.name + " 名额已满（" + course.capacity + "）");

        Enrollment e = new Enrollment();
        e.memberId = f.memberId;
        e.courseId = f.courseId;
        e.status = "已报";
        e.enrollDate = today();
        e = repo.save(e);
        recountCourse(f.courseId);
        return e;
    }

    /**
     * 退课 / 状态更新：退课后容量释放（重算课程已报人数）。
     */
    public Enrollment update(Long id, Enrollment f) {
        Enrollment e = repo.findById(id).orElseThrow(() -> new BizException("报名记录不存在"));
        if (f.status != null && !f.status.isBlank()) {
            e.status = f.status;
        }
        e = repo.save(e);
        recountCourse(e.courseId);
        return e;
    }

    private void recountCourse(Long courseId) {
        if (courseId == null) return;
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null) return;
        c.enrolled = (int) repo.countActiveByCourseId(courseId);
        courseRepo.save(c);
    }
}

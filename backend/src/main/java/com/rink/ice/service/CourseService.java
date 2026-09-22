package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.Course;
import com.rink.ice.entity.IceLane;
import com.rink.ice.entity.ResurfaceWindow;
import com.rink.ice.repository.CourseRepository;
import com.rink.ice.repository.IceLaneRepository;
import com.rink.ice.repository.ResurfaceWindowRepository;
import com.rink.ice.util.IceTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {
    @Autowired
    CourseRepository repo;
    @Autowired
    IceLaneRepository laneRepo;
    @Autowired
    ResurfaceWindowRepository windowRepo;

    public List<Course> list() {
        List<ResurfaceWindow> windows = windowRepo.findAll();
        return repo.findAll().stream().map(c -> decorate(c, windows)).toList();
    }

    /**
     * 依据浇冰窗口实时标记课程：同冰面、同一天、时段相交即「浇冰中」。
     * 冻态不落库——窗口改到夜里 / 删除后，下一次查询自动解除。
     */
    private Course decorate(Course c, List<ResurfaceWindow> windows) {
        List<ResurfaceWindow> hit = IceTime.freezingWindows(
                c.laneId, c.sessionDate, c.startTime, c.endTime, windows);
        c.resurfacing = !hit.isEmpty();
        c.resurfaceInfo = hit.isEmpty() ? null
                : c.sessionDate + " " + hit.get(0).startTime + "-" + hit.get(0).endTime
                + " 浇冰（" + hit.get(0).operator + "）";
        return c;
    }

    public Course create(Course f) {
        if (f.laneId == null) throw new BizException("归属冰面必填");
        IceLane lane = laneRepo.findById(f.laneId)
                .orElseThrow(() -> new BizException("归属冰面不存在"));
        if (!"开放".equals(lane.status))
            throw new BizException("归属冰面 " + lane.code + " 非开放状态，不可排课");
        if (f.name == null || f.name.isBlank()) throw new BizException("课程名称必填");
        if (f.capacity == null || f.capacity <= 0) throw new BizException("课程容量必须大于 0");
        validateSchedule(f);

        Course e = new Course();
        e.laneId = f.laneId;
        e.name = f.name;
        e.capacity = f.capacity;
        e.enrolled = 0;
        applySchedule(e, f);
        return repo.save(e);
    }

    public Course update(Long id, Course f) {
        Course e = repo.findById(id).orElseThrow(() -> new BizException("课程不存在"));
        if (f.laneId != null) {
            IceLane lane = laneRepo.findById(f.laneId)
                    .orElseThrow(() -> new BizException("归属冰面不存在"));
            if (!"开放".equals(lane.status))
                throw new BizException("归属冰面 " + lane.code + " 非开放状态，不可排课");
            e.laneId = f.laneId;
        }
        if (f.name != null && !f.name.isBlank()) e.name = f.name;
        if (f.capacity != null) {
            if (f.capacity <= 0) throw new BizException("课程容量必须大于 0");
            // 允许把容量改到当前已报人数以下：已报名的学员不被清退（只压不踢），
            // 报名事务在锁下按新容量判定，enrolled >= capacity 期间新报名立即被拦；
            // 容量改回去/改大后无需任何额外操作，下一次报名自然按新容量放行。
            e.capacity = f.capacity;
        }
        // enrolled 是名额占用的派生计数，只由报名/退课事务维护，不接受柜台直接改写
        // 排期字段允许随课程编辑；浇冰窗口不挪课，只冻新报名
        if (f.sessionDate != null || f.startTime != null || f.endTime != null) {
            validateSchedule(f);
            applySchedule(e, f);
        }
        return repo.save(e);
    }

    /** 排期三项要么都给，要么都不给；给了就要合法且开始早于结束。 */
    private void validateSchedule(Course f) {
        boolean any = f.sessionDate != null || f.startTime != null || f.endTime != null;
        if (!any) return;
        if (f.sessionDate == null || f.startTime == null || f.endTime == null
                || f.sessionDate.isBlank() || f.startTime.isBlank() || f.endTime.isBlank()) {
            throw new BizException("上课日期/开始时间/结束时间需同时填写");
        }
        String date = IceTime.requireDate(f.sessionDate, "上课日期");
        String start = IceTime.requireTime(f.startTime, "开始时间");
        String end = IceTime.requireTime(f.endTime, "结束时间");
        if (start.compareTo(end) >= 0) throw new BizException("结束时间必须晚于开始时间");
        f.sessionDate = date;
        f.startTime = start;
        f.endTime = end;
    }

    private void applySchedule(Course e, Course f) {
        e.sessionDate = (f.sessionDate == null || f.sessionDate.isBlank()) ? null : f.sessionDate;
        e.startTime = (f.startTime == null || f.startTime.isBlank()) ? null : f.startTime;
        e.endTime = (f.endTime == null || f.endTime.isBlank()) ? null : f.endTime;
    }
}

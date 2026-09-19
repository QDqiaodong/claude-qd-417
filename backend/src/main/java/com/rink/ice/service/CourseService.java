package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.Course;
import com.rink.ice.entity.IceLane;
import com.rink.ice.repository.CourseRepository;
import com.rink.ice.repository.IceLaneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {
    @Autowired
    CourseRepository repo;
    @Autowired
    IceLaneRepository laneRepo;

    public List<Course> list() {
        return repo.findAll();
    }

    public Course create(Course f) {
        if (f.laneId == null) throw new BizException("归属冰面必填");
        IceLane lane = laneRepo.findById(f.laneId)
                .orElseThrow(() -> new BizException("归属冰面不存在"));
        if (!"开放".equals(lane.status))
            throw new BizException("归属冰面 " + lane.code + " 非开放状态，不可排课");
        if (f.name == null || f.name.isBlank()) throw new BizException("课程名称必填");
        if (f.capacity == null || f.capacity <= 0) throw new BizException("课程容量必须大于 0");
        Course e = new Course();
        e.laneId = f.laneId;
        e.name = f.name;
        e.capacity = f.capacity;
        e.enrolled = 0;
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
            if (f.capacity < e.enrolled) throw new BizException("容量不能小于当前已报人数 " + e.enrolled);
            e.capacity = f.capacity;
        }
        if (f.enrolled != null) {
            if (f.enrolled < 0) throw new BizException("已报人数不能为负");
            if (f.enrolled > e.capacity) throw new BizException("已报人数不可超过容量 " + e.capacity);
            e.enrolled = f.enrolled;
        }
        return repo.save(e);
    }
}

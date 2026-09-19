package com.rink.ice.controller;

import com.rink.ice.entity.Enrollment;
import com.rink.ice.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    @Autowired
    EnrollmentService service;

    @GetMapping
    public List<Enrollment> list() {
        return service.list();
    }

    // 报名（会员×课程多对多，落所有业务规则）
    @PostMapping
    public Enrollment enroll(@RequestBody Enrollment f) {
        return service.enroll(f);
    }

    // 退课 / 状态更新（退课后容量释放）
    @PutMapping("/{id}")
    public Enrollment update(@PathVariable Long id, @RequestBody Enrollment f) {
        return service.update(id, f);
    }
}

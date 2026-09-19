package com.rink.ice.controller;

import com.rink.ice.entity.Course;
import com.rink.ice.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    @Autowired
    CourseService service;

    @GetMapping
    public List<Course> list() {
        return service.list();
    }

    @PostMapping
    public Course create(@RequestBody Course f) {
        return service.create(f);
    }

    @PutMapping("/{id}")
    public Course update(@PathVariable Long id, @RequestBody Course f) {
        return service.update(id, f);
    }
}

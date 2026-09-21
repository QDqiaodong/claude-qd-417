package com.rink.ice.controller;

import com.rink.ice.entity.Member;
import com.rink.ice.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    @Autowired
    MemberService service;

    @GetMapping
    public List<Member> list() {
        return service.list();
    }

    @PostMapping
    public Member create(@RequestBody Member f) {
        return service.create(f);
    }

    @PutMapping("/{id}")
    public Member update(@PathVariable Long id, @RequestBody Member f) {
        return service.update(id, f);
    }
}

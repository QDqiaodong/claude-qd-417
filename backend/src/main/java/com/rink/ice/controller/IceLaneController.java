package com.rink.ice.controller;

import com.rink.ice.entity.IceLane;
import com.rink.ice.service.IceLaneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ice-lanes")
public class IceLaneController {
    @Autowired
    IceLaneService service;

    @GetMapping
    public List<IceLane> list() {
        return service.list();
    }

    @PostMapping
    public IceLane create(@RequestBody IceLane f) {
        return service.create(f);
    }

    @PutMapping("/{id}")
    public IceLane update(@PathVariable Long id, @RequestBody IceLane f) {
        return service.update(id, f);
    }
}

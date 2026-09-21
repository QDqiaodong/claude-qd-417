package com.rink.ice.controller;

import com.rink.ice.entity.ResurfaceWindow;
import com.rink.ice.service.ResurfaceWindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resurface-windows")
public class ResurfaceWindowController {
    @Autowired
    ResurfaceWindowService service;

    @GetMapping
    public List<ResurfaceWindow> list() {
        return service.list();
    }

    // 落浇冰窗口：同冰同日时段相交则整单回掉；冰面已关闭则建不起来
    @PostMapping
    public ResurfaceWindow create(@RequestBody ResurfaceWindow f) {
        return service.create(f);
    }

    // 改窗口（如白天改到打烊后）：挪走后原先冻住的课自动恢复报名
    @PutMapping("/{id}")
    public ResurfaceWindow update(@PathVariable Long id, @RequestBody ResurfaceWindow f) {
        return service.update(id, f);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

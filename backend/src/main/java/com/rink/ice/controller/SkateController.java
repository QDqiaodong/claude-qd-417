package com.rink.ice.controller;

import com.rink.ice.dto.SkateRequest;
import com.rink.ice.entity.Skate;
import com.rink.ice.entity.SkateRental;
import com.rink.ice.service.SkateRentalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skates")
public class SkateController {
    @Autowired
    SkateRentalService service;

    // 冰刀实物：按尺码看可借 / 已借 / 待检
    @GetMapping
    public List<Skate> listSkates() {
        return service.listSkates();
    }

    // 待检冰刀检修完成，重新上架可借
    @PutMapping("/{id}/inspect-done")
    public Skate inspectDone(@PathVariable Long id) {
        return service.inspectDone(id);
    }

    // 租借记录：会员 / 课程 / 实际那双冰刀编号 / 当前状态
    @GetMapping("/rentals")
    public List<SkateRental> listRentals() {
        return service.listRentals();
    }

    // 发鞋（领取）：按尺码真正占住一双可借冰刀，并发抢最后一双时败方整单 400
    @PostMapping("/rentals")
    public SkateRental pickUp(@RequestBody SkateRequest req) {
        return service.pickUp(req);
    }

    // 归还：damaged=false 正常归还；damaged=true 刀刃损坏（冰刀转待检，单据仍收口）
    @PutMapping("/rentals/{id}/return")
    public SkateRental giveBack(@PathVariable Long id, @RequestBody(required = false) SkateRequest req) {
        SkateRequest body = req == null ? new SkateRequest() : req;
        body.rentalId = id;
        return service.giveBack(body);
    }
}

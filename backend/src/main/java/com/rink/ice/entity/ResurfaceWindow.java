package com.rink.ice.entity;

import jakarta.persistence.*;

/**
 * 浇冰窗口：磨冰车在指定冰面、指定日期的一段时间内作业。
 * 临时占冰，不是维护/关闭——不改变冰面状态、不清已报名、不改挂课程。
 * 同一块冰、同一天时段相交的两段窗口，后落的整单回掉，先落的继续压课。
 */
@Entity
@Table(name = "resurface_window")
public class ResurfaceWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "lane_id", nullable = false)
    public Long laneId;

    @Column(name = "win_date", nullable = false)
    public String winDate;

    @Column(name = "start_time", nullable = false)
    public String startTime;

    @Column(name = "end_time", nullable = false)
    public String endTime;

    @Column(name = "operator", nullable = false)
    public String operator;
}

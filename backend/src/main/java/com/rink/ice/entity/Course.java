package com.rink.ice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "course")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "lane_id", nullable = false)
    public Long laneId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "capacity", nullable = false)
    public Integer capacity;

    @Column(name = "enrolled", nullable = false)
    public Integer enrolled;

    // 排期：哪一天 yyyy-MM-dd、从几点 HH:mm、到几点 HH:mm（允许为空，表示暂未排期）
    @Column(name = "session_date")
    public String sessionDate;

    @Column(name = "start_time")
    public String startTime;

    @Column(name = "end_time")
    public String endTime;

    // —— 非持久化的派生状态：该课程当前是否正被浇冰窗口压住（冻新报名）——
    // 不落库，每次查询由浇冰窗口实时计算；窗口改到夜里/删除后自动解除。
    @Transient
    @JsonProperty("resurfacing")
    public boolean resurfacing = false;

    @Transient
    @JsonProperty("resurfaceInfo")
    public String resurfaceInfo;
}

package com.rink.ice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * 冰刀（实物，一双一条记录）。
 * code 唯一编号；size 尺码；status 可用状态：
 * - 可借：在架，可被租借单占用
 * - 已借：已被某张「已领取」租借单实际占住
 * - 待检：归还时标记刀刃损坏，等待检修，不能立即释放给下一位
 */
@Entity
@Table(name = "skate")
public class Skate {
    public static final String STATUS_AVAILABLE = "可借";
    public static final String STATUS_BORROWED = "已借";
    public static final String STATUS_INSPECTING = "待检";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "code", nullable = false, unique = true)
    public String code;

    @Column(name = "shoe_size", nullable = false)
    public Integer shoeSize;

    @Column(name = "status", nullable = false)
    public String status;

    // —— 非持久化的展示字段：已借/待检时当前或最后一张关联租借单的信息 ——
    @Transient
    @JsonProperty("rentalId")
    public Long rentalId;

    @Transient
    @JsonProperty("memberName")
    public String memberName;

    @Transient
    @JsonProperty("courseName")
    public String courseName;
}

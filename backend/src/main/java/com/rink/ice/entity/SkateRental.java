package com.rink.ice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * 冰刀租借单（交接凭证）。
 * 一张单同时拴住：会员 memberId、当天课程 courseId、实际领走的那双冰刀 skateId。
 * 不允许存在没有冰刀的空单：发鞋事务内先占住一双「可借」冰刀再落单，整单同生共死。
 *
 * 状态机：
 * - 已领取：冰刀在会员手上，对应 ice skate.status = 已借
 * - 已归还：正常归还，冰刀回到 可借，可再被下一张单占用
 * - 损坏归还：刀刃损坏，单据照常完整收口，但冰刀转为 待检，不立即释放
 */
@Entity
@Table(name = "skate_rental")
public class SkateRental {
    public static final String STATUS_PICKED = "已领取";
    public static final String STATUS_RETURNED = "已归还";
    public static final String STATUS_DAMAGED = "损坏归还";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "member_id", nullable = false)
    public Long memberId;

    @Column(name = "course_id", nullable = false)
    public Long courseId;

    // 实际领走的那双冰刀；发鞋成功即非空，数据库层面再用唯一键保证一双冰刀至多一张在借单
    @Column(name = "skate_id", nullable = false)
    public Long skateId;

    @Column(name = "shoe_size", nullable = false)
    public Integer shoeSize;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "rent_date", nullable = false)
    public String rentDate;

    @Column(name = "rent_time", nullable = false)
    public String rentTime;

    @Column(name = "return_date")
    public String returnDate;

    @Column(name = "return_time")
    public String returnTime;

    // 损坏归还时登记的刀刃问题
    @Column(name = "damage_note")
    public String damageNote;

    // —— 非持久化的展示字段：列表页直接带出会员 / 课程 / 冰刀编号 ——
    @Transient
    @JsonProperty("memberName")
    public String memberName;

    @Transient
    @JsonProperty("memberCard")
    public String memberCard;

    @Transient
    @JsonProperty("courseName")
    public String courseName;

    @Transient
    @JsonProperty("skateCode")
    public String skateCode;
}

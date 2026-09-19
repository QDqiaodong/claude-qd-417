package com.rink.ice.entity;

import jakarta.persistence.*;

/**
 * 会员与课程的多对多中间实体（显式）。
 * 不直接用 @ManyToMany 隐式中间表，因为它无法承载 status / enrollDate 等业务状态。
 * memberId / courseId 为外键列，业务校验在 Service 层完成。
 */
@Entity
@Table(name = "enrollment")
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "member_id", nullable = false)
    public Long memberId;

    @Column(name = "course_id", nullable = false)
    public Long courseId;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "enroll_date", nullable = false)
    public String enrollDate;
}

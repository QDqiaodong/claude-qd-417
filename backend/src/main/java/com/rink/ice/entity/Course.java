package com.rink.ice.entity;

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
}

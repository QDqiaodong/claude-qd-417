package com.rink.ice.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ice_lane")
public class IceLane {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "code", nullable = false, unique = true)
    public String code;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "status", nullable = false)
    public String status;
}

package com.rink.ice.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "card_no", nullable = false, unique = true)
    public String cardNo;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "expire_date", nullable = false)
    public String expireDate;

    @Column(name = "status", nullable = false)
    public String status;
}

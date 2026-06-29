package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "coupons")
@Data
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private Double discountPercentage;

    @Column(nullable = true)
    private String assignedUserEmail;

    private Boolean active = true;

    public Coupon() {}

    public Coupon(String code, Double discountPercentage, String assignedUserEmail, Boolean active) {
        this.code = code != null ? code.toUpperCase().trim() : null;
        this.discountPercentage = discountPercentage;
        this.assignedUserEmail = assignedUserEmail != null ? assignedUserEmail.trim() : null;
        this.active = active != null ? active : true;
    }
}

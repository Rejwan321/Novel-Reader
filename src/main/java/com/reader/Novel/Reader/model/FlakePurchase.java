package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "flake_purchases")
@Data
public class FlakePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer amount;
    private Double price;
    private LocalDateTime purchasedAt;

    public FlakePurchase() {}

    public FlakePurchase(Long userId, Integer amount, Double price, LocalDateTime purchasedAt) {
        this.userId = userId;
        this.amount = amount;
        this.price = price;
        this.purchasedAt = purchasedAt;
    }
}

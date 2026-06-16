package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchases")
@Data
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long chapterId;
    private LocalDateTime purchasedAt;

    public Purchase() {}

    public Purchase(Long id, Long userId, Long chapterId, LocalDateTime purchasedAt) {
        this.id = id;
        this.userId = userId;
        this.chapterId = chapterId;
        this.purchasedAt = purchasedAt;
    }
}

package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "ratings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "novelId"})
})
@Data
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long novelId;
    private Integer ratingValue; // 1 to 5

    public Rating() {}

    public Rating(Long id, Long userId, Long novelId, Integer ratingValue) {
        this.id = id;
        this.userId = userId;
        this.novelId = novelId;
        this.ratingValue = ratingValue;
    }
}

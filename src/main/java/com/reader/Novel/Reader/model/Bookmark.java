package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookmarks", uniqueConstraints = {@UniqueConstraint(columnNames = {"userId", "novelId"})})
@Data
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long novelId;
    private Double lastReadChapterNumber;
    private LocalDateTime updatedAt;

    @Transient
    private Novel novel;

    public Bookmark() {}

    public Bookmark(Long id, Long userId, Long novelId, Double lastReadChapterNumber, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.novelId = novelId;
        this.lastReadChapterNumber = lastReadChapterNumber;
        this.updatedAt = updatedAt;
    }
}

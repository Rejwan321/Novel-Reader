package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "comment_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Comment comment;

    @Column(nullable = false)
    private String mentionerName;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String snippet;

    @Column(nullable = false)
    private Long novelId;

    @Column(nullable = false)
    private String novelTitle;

    @Column(nullable = false)
    private Double chapterNumber;

    @Column(nullable = false)
    private Long chapterId;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = true)
    private Long userId;

    public Notification() {
    }

    public Notification(Comment comment, String mentionerName, String snippet, Long novelId, String novelTitle, Double chapterNumber, Long chapterId) {
        this.comment = comment;
        this.mentionerName = mentionerName;
        this.snippet = snippet;
        this.novelId = novelId;
        this.novelTitle = novelTitle;
        this.chapterNumber = chapterNumber;
        this.chapterId = chapterId;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Notification(Comment comment, String mentionerName, String snippet, Long novelId, String novelTitle, Double chapterNumber, Long chapterId, Long userId) {
        this.comment = comment;
        this.mentionerName = mentionerName;
        this.snippet = snippet;
        this.novelId = novelId;
        this.novelTitle = novelTitle;
        this.chapterNumber = chapterNumber;
        this.chapterId = chapterId;
        this.userId = userId;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

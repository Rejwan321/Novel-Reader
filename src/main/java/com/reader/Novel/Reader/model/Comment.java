package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chapterId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Comment() {
    }

    public Comment(Long chapterId, User user, String content) {
        this.chapterId = chapterId;
        this.user = user;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}

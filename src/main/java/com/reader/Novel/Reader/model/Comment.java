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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Comment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private java.util.List<Comment> replies = new java.util.ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "comment_likes", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "user_id")
    private java.util.Set<Long> likedUserIds = new java.util.HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "comment_dislikes", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "user_id")
    private java.util.Set<Long> dislikedUserIds = new java.util.HashSet<>();

    public Comment() {
    }

    public Comment(Long chapterId, User user, String content) {
        this.chapterId = chapterId;
        this.user = user;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}

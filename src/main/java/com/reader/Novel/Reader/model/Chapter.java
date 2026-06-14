package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "chapters")
@Data
@JsonIgnoreProperties({"novel"})
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    private String title;
    private Double chapterNumber;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String content; // text content for Novel, or comma-separated image URLs for Comic
    private Integer price = 0;

    public Chapter() {}

    public Chapter(Long id, Novel novel, String title, Double chapterNumber, String content) {
        this.id = id;
        this.novel = novel;
        this.title = title;
        this.chapterNumber = chapterNumber;
        this.content = content;
        this.price = 0;
    }

    public Chapter(Long id, Novel novel, String title, Double chapterNumber, String content, Integer price) {
        this.id = id;
        this.novel = novel;
        this.title = title;
        this.chapterNumber = chapterNumber;
        this.content = content;
        this.price = price;
    }
}

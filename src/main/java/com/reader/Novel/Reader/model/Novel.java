package com.reader.Novel.Reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "novels")
@Data
@JsonIgnoreProperties({"chapters"})
public class Novel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String author;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String description;

    private String coverUrl;
    private String type; // "NOVEL" or "COMIC"
    private String genre; // e.g. "Fantasy", "Action", "Romance", "Sci-Fi"
    private Double rating;
    private String status; // "ONGOING" or "COMPLETED"
    private Long creatorId;
    @Column(name = "release_year")
    private Integer year;
    @Lob
    @Column(columnDefinition = "CLOB")
    private String tags;
    private String countryOfOrigin;
    private String source;

    @OneToMany(mappedBy = "novel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("chapterNumber ASC")
    private List<Chapter> chapters = new ArrayList<>();

    public Novel() {}

    public Novel(Long id, String title, String author, String description, String coverUrl, String type, String genre, Double rating, String status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.coverUrl = coverUrl;
        this.type = type;
        this.genre = genre;
        this.rating = rating;
        this.status = status;
    }
}

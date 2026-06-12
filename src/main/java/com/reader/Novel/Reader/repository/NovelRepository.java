package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NovelRepository extends JpaRepository<Novel, Long> {
    List<Novel> findByType(String type);
    List<Novel> findByGenreContainingIgnoreCase(String genre);
    List<Novel> findByTitleContainingIgnoreCase(String title);
    List<Novel> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrGenreContainingIgnoreCase(String title, String author, String genre);
    List<Novel> findByCreatorId(Long creatorId);
}

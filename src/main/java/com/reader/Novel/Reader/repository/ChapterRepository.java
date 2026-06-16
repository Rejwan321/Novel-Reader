package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByNovelIdOrderByChapterNumberAsc(Long novelId);
    Optional<Chapter> findByNovelIdAndChapterNumber(Long novelId, Double chapterNumber);
    List<Chapter> findByPublishAtAfterOrderByPublishAtAsc(java.time.LocalDateTime dateTime);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Chapter c WHERE c.publishNotificationSent IS NULL OR c.publishNotificationSent = false")
    List<Chapter> findUnnotifiedChapters();
}

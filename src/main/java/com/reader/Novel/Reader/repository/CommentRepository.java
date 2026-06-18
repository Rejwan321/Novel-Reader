package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByChapterIdOrderByCreatedAtAsc(Long chapterId);
    List<Comment> findByChapterIdAndParentIsNullOrderByCreatedAtAsc(Long chapterId);
    List<Comment> findByUserId(Long userId);
    void deleteByChapterId(Long chapterId);
}

package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Bookmark> findByUserIdAndNovelId(Long userId, Long novelId);
    void deleteByUserIdAndNovelId(Long userId, Long novelId);
    void deleteByNovelId(Long novelId);
}

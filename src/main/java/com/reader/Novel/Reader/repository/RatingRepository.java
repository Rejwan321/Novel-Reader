package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByUserIdAndNovelId(Long userId, Long novelId);
    List<Rating> findByNovelId(Long novelId);
    List<Rating> findByUserId(Long userId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Long userId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByNovelId(Long novelId);
}

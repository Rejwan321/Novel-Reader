package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByUserId(Long userId);
    Optional<Purchase> findByUserIdAndChapterId(Long userId, Long chapterId);
    void deleteByUserId(Long userId);
    void deleteByChapterId(Long chapterId);
}


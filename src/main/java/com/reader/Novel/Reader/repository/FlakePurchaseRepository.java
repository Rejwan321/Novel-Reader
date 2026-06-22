package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.FlakePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FlakePurchaseRepository extends JpaRepository<FlakePurchase, Long> {
    List<FlakePurchase> findByUserIdOrderByPurchasedAtDesc(Long userId);
}

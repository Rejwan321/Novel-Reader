package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.FlakePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlakePackageRepository extends JpaRepository<FlakePackage, Long> {
    List<FlakePackage> findAllByOrderByAmountAsc();
}

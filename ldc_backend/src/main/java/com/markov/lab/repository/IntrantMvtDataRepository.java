package com.markov.lab.repository;

import com.markov.lab.entity.IntrantMvtData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntrantMvtDataRepository extends JpaRepository<IntrantMvtData, Long> {
    boolean existsByReport_Id(Long reportId);
    void deleteByReport_Id(Long reportId);
}

package com.markov.lab.repository;

import com.markov.lab.entity.LabActivityData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabActivityDataRepository extends JpaRepository<LabActivityData, Long> {
    boolean existsByReport_Id(Long reportId);
    void deleteByReport_Id(Long reportId);
}

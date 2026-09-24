package com.markov.lab.repository;

import com.markov.lab.entity.SapNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SapNotificationRepository extends JpaRepository<SapNotification, Long> {
    long countByEquipment_Id(Long equipmentId);

    long countByIntrant_Id(Long intrantId);
}

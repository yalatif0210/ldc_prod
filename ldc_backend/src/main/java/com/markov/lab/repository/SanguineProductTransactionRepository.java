package com.markov.lab.repository;

import com.markov.lab.entity.SanguineProductTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanguineProductTransactionRepository extends JpaRepository<SanguineProductTransaction, Long> {
    long countBySanguineProduct_Id(Long sanguineProductId);
}

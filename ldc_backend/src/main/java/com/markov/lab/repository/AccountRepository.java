package com.markov.lab.repository;

import com.markov.lab.entity.Account;
import com.markov.lab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUser(User user);

    @Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.role LEFT JOIN FETCH a.structures LEFT JOIN FETCH a.user")
    List<Account> findAllWithDetails();
}

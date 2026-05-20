package com.markov.lab.controller;

import com.markov.lab.entity.Account;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.User;
import com.markov.lab.input.AccountInput;
import com.markov.lab.repository.AccountRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;


import org.springframework.stereotype.Controller;


import java.util.List;

@Controller
@RequiredArgsConstructor
class AccountController {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;


    @QueryMapping
    public List<Account> accounts() {
        return accountRepository.findAll();
    }

    @QueryMapping
    public Account account(@Argument Long id) {
        return accountRepository.findById(id).orElse(null);
    }

    @MutationMapping
    public Account createAccount(@Argument AccountInput accountInput) {
        Account account = new Account();
        userRepository.findById(accountInput.getUserId()).ifPresent(account::setUser);
        roleRepository.findById(accountInput.getRoleId()).ifPresent(account::setRole);
        return accountRepository.save(account);
    }

    @MutationMapping
    public Account updateAccount(@Argument Long id, @Argument AccountInput accountInput) {
        return accountRepository.findById(id)
                .map( existingaccount -> {
                    userRepository.findById(accountInput.getUserId()).ifPresent(existingaccount::setUser);
                    roleRepository.findById(accountInput.getRoleId()).ifPresent(existingaccount::setRole);
                    return accountRepository.save(existingaccount);
                })
                .orElse(null);
    }

    @MutationMapping
    public Boolean deleteAccount(@Argument Long id) {
        try {
            accountRepository.deleteById(id);
            return true;
        }catch (EmptyResultDataAccessException ex){
            return false;
        }
    }
}

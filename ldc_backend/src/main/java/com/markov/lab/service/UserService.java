package com.markov.lab.service;

import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.Account;
import com.markov.lab.entity.Structure;
import com.markov.lab.entity.User;
import com.markov.lab.exceptions.InvalidCurrentPasswordException;
import com.markov.lab.input.UserInput;

import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;
    private final StructureRepository structureRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository repository, StructureRepository structureRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.structureRepository = structureRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User update(Long id, UserInput input) {
        return repository.findById(id).map(user -> {
            if (input.getName() != null)     user.setName(input.getName());
            if (input.getUsername() != null) user.setUsername(input.getUsername());
            if (input.getPhone() != null)    user.setPhone(input.getPhone());
            if (input.getPassword() != null && !input.getPassword().isBlank())
                user.setPassword(passwordEncoder.encode(input.getPassword()));
            return repository.save(user);
        }).orElse(null);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = repository.findByUsername(username).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
    }

    @Transactional
    public User save(SignupRequest request) {
        Optional<User> existingUser = repository.findByUsername(request.username());
        if (existingUser.isPresent()) {
            throw new RuntimeException("Cet utilisateur est déjà configuré.");
        }
        User user = this.createUser(request, new User());
        return repository.save(user);
    }

    private User createUser(SignupRequest request, User user) {
        int role = request.role();
        Account account = new Account();
        if (role == 1) {
            // SUPER_ADMIN : accès à toutes les Structures, non restreignable (docs/adr/0001-admin-role-scoped-structure-access.md)
            account.getStructures().addAll(structureRepository.findAll());
        } else {
            account.getStructures().addAll(structureRepository.findByIdList(request.platforms()));
        }
        account.setRole(roleRepository.findById((long) role).orElse(null));
        account.setIsActive(true);
        //System.out.println(account);
        String hashedPassword = passwordEncoder.encode(request.password());
        user.setUsername(request.username());
        user.setPassword(hashedPassword);
        user.setName(request.name());
        user.setPhone(request.phone());
        user.setAccount(account);
        return user;
    }

}

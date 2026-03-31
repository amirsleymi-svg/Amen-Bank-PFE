package com.amenbank.service.impl;

import com.amenbank.entity.Admin;
import com.amenbank.entity.User;
import com.amenbank.repository.AdminRepository;
import com.amenbank.repository.RoleRepository;
import com.amenbank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Try the clients/users table first
        Optional<User> userOpt = userRepository.findByEmailOrUsername(username);
        if (userOpt.isPresent()) {
            return buildUserDetails(userOpt.get());
        }

        // 2. Fall back to the admin/employee table
        Optional<Admin> adminOpt = adminRepository.findByEmailOrUsername(username);
        if (adminOpt.isPresent()) {
            return buildAdminDetails(adminOpt.get());
        }

        throw new UsernameNotFoundException("Account not found: " + username);
    }

    @Transactional(readOnly = true)
    public User loadUserEntityByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailOrUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Admin loadAdminEntityByUsername(String username) throws UsernameNotFoundException {
        return adminRepository.findByEmailOrUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + username));
    }

    // ── Build UserDetails for a client/user entity ────────────────────
    private UserDetails buildUserDetails(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .flatMap(role -> Stream.concat(
                        Stream.of(new SimpleGrantedAuthority(role.getName())),
                        role.getPermissions().stream()
                                .map(p -> new SimpleGrantedAuthority(p.getName()))
                ))
                .distinct()
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!user.isAccountNonLocked())
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .build();
    }

    // ── Build UserDetails for an admin/employee entity ────────────────
    private UserDetails buildAdminDetails(Admin admin) {
        String roleName = "ROLE_" + admin.getRole().name(); // ROLE_ADMIN or ROLE_EMPLOYEE

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleName));

        // Load permissions from the roles table for the corresponding role
        roleRepository.findByName(roleName).ifPresent(role ->
                role.getPermissions().forEach(p ->
                        authorities.add(new SimpleGrantedAuthority(p.getName()))
                )
        );

        return org.springframework.security.core.userdetails.User.builder()
                .username(admin.getEmail())
                .password(admin.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!admin.isAccountNonLocked())
                .credentialsExpired(false)
                .disabled(!admin.getActive())
                .build();
    }
}

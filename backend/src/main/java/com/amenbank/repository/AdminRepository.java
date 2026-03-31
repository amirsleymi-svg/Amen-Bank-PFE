package com.amenbank.repository;

import com.amenbank.entity.Admin;
import com.amenbank.enums.AdminRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmail(String email);
    Optional<Admin> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("SELECT a FROM Admin a WHERE a.email = :identifier OR a.username = :identifier")
    Optional<Admin> findByEmailOrUsername(@Param("identifier") String identifier);

    Page<Admin> findByRole(AdminRole role, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Admin a SET a.failedLoginAttempts = 0, a.lockedUntil = null WHERE a.id = :id")
    void resetLoginAttempts(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Admin a SET a.failedLoginAttempts = a.failedLoginAttempts + 1, " +
           "a.lockedUntil = :lockedUntil WHERE a.id = :id")
    void incrementFailedAttempts(@Param("id") Long id, @Param("lockedUntil") LocalDateTime lockedUntil);
}

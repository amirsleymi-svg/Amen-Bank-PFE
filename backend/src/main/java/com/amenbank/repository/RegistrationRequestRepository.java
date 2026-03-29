package com.amenbank.repository;

import com.amenbank.entity.RegistrationRequest;
import com.amenbank.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    boolean existsByEmail(String email);

    Optional<RegistrationRequest> findByEmail(String email);

    Page<RegistrationRequest> findByStatusOrderByCreatedAtDesc(RegistrationStatus status, Pageable pageable);

    Page<RegistrationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

package com.amenbank.repository;

import com.amenbank.entity.KycRequest;
import com.amenbank.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface KycRequestRepository extends JpaRepository<KycRequest, Long> {
    Optional<KycRequest> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    Page<KycRequest> findByStatusOrderByCreatedAtDesc(KycStatus status, Pageable pageable);
}

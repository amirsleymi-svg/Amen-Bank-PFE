package com.amenbank.repository;

import com.amenbank.entity.CreditApplication;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.CreditType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditApplicationRepository extends JpaRepository<CreditApplication, Long> {
    Page<CreditApplication> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<CreditApplication> findByStatusOrderByCreatedAtDesc(CreditStatus status, Pageable pageable);
    Page<CreditApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByUserIdAndCreditTypeAndStatus(Long userId, CreditType creditType, CreditStatus status);
}

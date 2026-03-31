package com.amenbank.repository;

import com.amenbank.entity.Transfer;
import com.amenbank.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Page<Transfer> findByFromAccountUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Transfer> findByStatusAndScheduledDateLessThanEqual(TransferStatus status, LocalDate date);

    // Employee/Admin: list transfers filtered by status
    Page<Transfer> findByStatus(TransferStatus status, Pageable pageable);
}

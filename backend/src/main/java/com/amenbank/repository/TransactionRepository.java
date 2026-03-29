package com.amenbank.repository;

import com.amenbank.entity.Transaction;
import com.amenbank.enums.TransactionStatus;
import com.amenbank.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
           "AND (:from IS NULL OR t.valueDate >= :from) " +
           "AND (:to IS NULL OR t.valueDate <= :to) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findFiltered(
        @Param("accountId") Long accountId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("type") TransactionType type,
        @Param("status") TransactionStatus status,
        Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
           "AND (:from IS NULL OR t.valueDate >= :from) " +
           "AND (:to IS NULL OR t.valueDate <= :to) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findFilteredForExport(
        @Param("accountId") Long accountId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("type") TransactionType type,
        @Param("status") TransactionStatus status);

    Optional<Transaction> findByTransactionRef(String ref);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.account.id = :accountId AND t.type = 'DEBIT' " +
           "AND t.valueDate = :today AND t.status = 'COMPLETED'")
    BigDecimal sumTodayDebits(@Param("accountId") Long accountId, @Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.account.id = :accountId AND t.type = 'DEBIT' " +
           "AND YEAR(t.valueDate) = YEAR(:month) AND MONTH(t.valueDate) = MONTH(:month) " +
           "AND t.status = 'COMPLETED'")
    BigDecimal sumMonthDebits(@Param("accountId") Long accountId, @Param("month") LocalDate month);
}

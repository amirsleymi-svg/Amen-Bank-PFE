package com.amenbank.repository;

import com.amenbank.entity.Account;
import com.amenbank.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserIdAndStatus(Long userId, AccountStatus status);
    List<Account> findByUserId(Long userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByIban(String iban);
    boolean existsByIban(String iban);
    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.id = :accountId AND a.status = 'ACTIVE'")
    Optional<Account> findActiveAccountByIdAndUserId(@Param("accountId") Long accountId, @Param("userId") Long userId);
}

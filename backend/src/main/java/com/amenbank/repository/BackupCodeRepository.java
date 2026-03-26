package com.amenbank.repository;

import com.amenbank.entity.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupCodeRepository extends JpaRepository<BackupCode, Long> {
    List<BackupCode> findByUserIdAndUsedFalse(Long userId);
    Optional<BackupCode> findByUserIdAndCodeHashAndUsedFalse(Long userId, String codeHash);
    void deleteAllByUserId(Long userId);
}

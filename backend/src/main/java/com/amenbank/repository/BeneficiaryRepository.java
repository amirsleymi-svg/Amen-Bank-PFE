package com.amenbank.repository;

import com.amenbank.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserId(Long userId);
    Optional<Beneficiary> findByUserIdAndIban(Long userId, String iban);
    boolean existsByUserIdAndIban(Long userId, String iban);
}

package com.amenbank.repository;

import com.amenbank.entity.CreditDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditDocumentRepository extends JpaRepository<CreditDocument, Long> {
    List<CreditDocument> findByApplicationId(Long applicationId);
}

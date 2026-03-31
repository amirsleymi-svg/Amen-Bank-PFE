package com.amenbank.dto.response;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.CreditType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditApplicationResponse {
    private Long id;
    private BigDecimal amount;
    private Integer durationMonths;
    private BigDecimal annualRate;
    private BigDecimal monthlyPayment;
    private BigDecimal totalCost;
    private String purpose;
    private CreditType creditType;
    private CreditStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    // Client info (populated for employee/admin views)
    private String clientName;
    private String clientEmail;
    // Who reviewed this application
    private String reviewedBy;
}

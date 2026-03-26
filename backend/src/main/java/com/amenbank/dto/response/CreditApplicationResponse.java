package com.amenbank.dto.response;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.CreditType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
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
}

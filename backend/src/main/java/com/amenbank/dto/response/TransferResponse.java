package com.amenbank.dto.response;
import com.amenbank.enums.TransferStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class TransferResponse {
    private Long id;
    private String fromAccountNumber;
    private String toIban;
    private String toName;
    private BigDecimal amount;
    private String currency;
    private String label;
    private TransferStatus status;
    private LocalDate scheduledDate;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}

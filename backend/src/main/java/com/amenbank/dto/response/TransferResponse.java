package com.amenbank.dto.response;
import com.amenbank.enums.TransferStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
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
    // Populated only for employee/admin views
    private String clientName;
    private String clientEmail;
}

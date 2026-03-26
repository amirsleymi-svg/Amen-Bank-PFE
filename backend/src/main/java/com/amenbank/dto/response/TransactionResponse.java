package com.amenbank.dto.response;
import com.amenbank.enums.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class TransactionResponse {
    private Long id;
    private String transactionRef;
    private TransactionType type;
    private TransactionCategory category;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String label;
    private TransactionStatus status;
    private String counterpartIban;
    private String counterpartName;
    private LocalDate valueDate;
    private LocalDateTime createdAt;
}

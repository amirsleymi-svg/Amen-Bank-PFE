package com.amenbank.dto.response;
import com.amenbank.enums.AccountStatus;
import com.amenbank.enums.AccountType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private String iban;
    private AccountType accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private AccountStatus status;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private LocalDateTime openedAt;
}

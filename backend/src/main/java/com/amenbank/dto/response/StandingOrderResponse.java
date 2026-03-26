package com.amenbank.dto.response;
import com.amenbank.enums.StandingOrderFrequency;
import com.amenbank.enums.StandingOrderStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class StandingOrderResponse {
    private Long id;
    private String fromAccountNumber;
    private String toIban;
    private String toName;
    private BigDecimal amount;
    private String currency;
    private String label;
    private StandingOrderFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextRunDate;
    private LocalDate lastRunDate;
    private StandingOrderStatus status;
    private LocalDateTime createdAt;
}

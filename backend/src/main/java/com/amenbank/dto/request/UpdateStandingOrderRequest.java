package com.amenbank.dto.request;

import com.amenbank.enums.StandingOrderFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateStandingOrderRequest {
    @DecimalMin("0.001") private BigDecimal amount;
    @Size(max = 255) private String label;
    private StandingOrderFrequency frequency;
    @Future private LocalDate startDate;
    private LocalDate endDate;
}

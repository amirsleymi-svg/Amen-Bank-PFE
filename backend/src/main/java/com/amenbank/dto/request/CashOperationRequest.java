package com.amenbank.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashOperationRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.001", message = "Amount must be positive")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;
}

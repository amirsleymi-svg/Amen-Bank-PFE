package com.amenbank.dto.request;
import com.amenbank.enums.StandingOrderFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StandingOrderRequest {
    @NotNull private Long fromAccountId;
    @NotBlank private String toIban;
    @NotBlank @Size(max = 150) private String toName;
    @NotNull @DecimalMin("0.001") private BigDecimal amount;
    @Size(max = 255) private String label;
    @NotNull private StandingOrderFrequency frequency;
    @NotNull @Future private LocalDate startDate;
    private LocalDate endDate;
}

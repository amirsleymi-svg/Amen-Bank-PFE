package com.amenbank.dto.request;
import com.amenbank.enums.CreditType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreditSimulationRequest {
    @NotNull @DecimalMin("1000.000") @DecimalMax("500000.000")
    private BigDecimal amount;

    @NotNull @Min(6) @Max(360)
    private Integer durationMonths;

    @NotNull
    private CreditType creditType;
}

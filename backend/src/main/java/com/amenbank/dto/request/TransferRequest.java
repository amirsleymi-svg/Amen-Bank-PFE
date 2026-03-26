package com.amenbank.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransferRequest {
    @NotNull private Long fromAccountId;

    @NotBlank @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}$", message = "Invalid IBAN format")
    private String toIban;

    @NotBlank @Size(max = 150) private String toName;

    @NotNull @DecimalMin(value = "0.001", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.999", message = "Amount exceeds maximum")
    private BigDecimal amount;

    @Size(max = 255) private String label;
    private LocalDate scheduledDate;

    @NotBlank @Pattern(regexp = "^[0-9]{6}$") private String totpCode;
}

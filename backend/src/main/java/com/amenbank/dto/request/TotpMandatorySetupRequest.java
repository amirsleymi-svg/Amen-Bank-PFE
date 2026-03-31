package com.amenbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpMandatorySetupRequest {

    @NotBlank(message = "Setup token is required")
    private String setupToken;
}

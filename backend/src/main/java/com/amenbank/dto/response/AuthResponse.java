package com.amenbank.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    private UserResponse user;
    // For 2FA step
    private Boolean totpRequired;
    private String tempToken;
}

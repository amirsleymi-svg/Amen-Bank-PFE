package com.amenbank.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    /** Populated for client/user logins */
    private UserResponse user;
    /** Populated for admin/employee logins */
    private AdminResponse adminUser;
    /** Actor type: "USER", "ADMIN", or "EMPLOYEE" */
    private String actorType;
    // 2FA: verify step (TOTP already enabled)
    private Boolean totpRequired;
    // 2FA: setup step (TOTP not yet configured — mandatory enrollment)
    private Boolean totpSetupRequired;
    private String tempToken;
    // Returned once after mandatory TOTP enrollment
    private java.util.List<String> backupCodes;
}

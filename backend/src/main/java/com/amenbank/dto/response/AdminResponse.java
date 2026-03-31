package com.amenbank.dto.response;

import com.amenbank.enums.AdminRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private AdminRole role;
    private Boolean totpEnabled;
    private Boolean active;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}

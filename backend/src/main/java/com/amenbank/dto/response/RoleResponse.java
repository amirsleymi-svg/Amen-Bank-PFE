package com.amenbank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.List;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleResponse {
    private Long id;
    private String name;
    private List<PermissionResponse> permissions;

    @Data @Builder
    public static class PermissionResponse {
        private Long id;
        private String name;
        private String description;
    }
}

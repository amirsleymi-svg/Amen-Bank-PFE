package com.amenbank.dto.response;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long pendingKycRequests;
    private long pendingCreditApplications;
    private long totalAccounts;
    private LocalDateTime generatedAt;
}

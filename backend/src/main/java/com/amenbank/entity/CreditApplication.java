package com.amenbank.entity;

import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.CreditType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 18, scale = 3) private BigDecimal amount;
    @Column(name = "duration_months", nullable = false) private Integer durationMonths;
    @Column(name = "annual_rate", nullable = false, precision = 5, scale = 4) private BigDecimal annualRate;
    @Column(name = "monthly_payment", nullable = false, precision = 18, scale = 3) private BigDecimal monthlyPayment;
    @Column(name = "total_cost", nullable = false, precision = 18, scale = 3) private BigDecimal totalCost;
    @Column(length = 255) private String purpose;

    @Enumerated(EnumType.STRING) @Column(name = "credit_type", nullable = false, length = 15) private CreditType creditType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) @Builder.Default private CreditStatus status = CreditStatus.PENDING;
    @Column(name = "rejection_reason", columnDefinition = "TEXT") private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private Admin reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "disbursed_at") private LocalDateTime disbursedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<CreditDocument> documents = new ArrayList<>();

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

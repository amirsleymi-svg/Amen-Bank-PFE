package com.amenbank.entity;

import com.amenbank.enums.StandingOrderFrequency;
import com.amenbank.enums.StandingOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "standing_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StandingOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @Column(name = "to_iban", nullable = false, length = 34) private String toIban;
    @Column(name = "to_name", nullable = false, length = 150) private String toName;
    @Column(nullable = false, precision = 18, scale = 3) private BigDecimal amount;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TND";
    @Column(length = 255) private String label;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private StandingOrderFrequency frequency;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "next_run_date", nullable = false) private LocalDate nextRunDate;
    @Column(name = "last_run_date") private LocalDate lastRunDate;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15)
    @Builder.Default private StandingOrderStatus status = StandingOrderStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

package com.amenbank.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "credit_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id", nullable = false)
    private CreditApplication application;
    @Column(name = "doc_type", nullable = false, length = 30) private String docType;
    @Column(name = "file_path", nullable = false, length = 512) private String filePath;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "mime_type", nullable = false, length = 100) private String mimeType;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private LocalDateTime uploadedAt;
    @PrePersist protected void onCreate() { uploadedAt = LocalDateTime.now(); }
}

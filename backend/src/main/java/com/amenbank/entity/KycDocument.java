package com.amenbank.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "kyc_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;
    @Column(name = "doc_type", nullable = false, length = 20) private String docType;
    @Column(name = "file_path", nullable = false, length = 512) private String filePath;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "mime_type", nullable = false, length = 100) private String mimeType;
    @Column(name = "file_size_bytes", nullable = false) private Long fileSizeBytes;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private LocalDateTime uploadedAt;
    @PrePersist protected void onCreate() { uploadedAt = LocalDateTime.now(); }
}

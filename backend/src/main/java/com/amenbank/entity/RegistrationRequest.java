package com.amenbank.entity;
 
 import com.amenbank.enums.RegistrationStatus;
 import jakarta.persistence.*;
 
 import java.time.LocalDateTime;
 
 @Entity
 @Table(name = "registration_requests")
 public class RegistrationRequest {
 
     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long id;
 
     @Column(nullable = false, unique = true, length = 150)
     private String email;
 
     @Enumerated(EnumType.STRING)
     @Column(nullable = false, length = 20)
     private RegistrationStatus status = RegistrationStatus.PENDING;
 
     @Column(name = "rejection_reason", columnDefinition = "TEXT")
     private String rejectionReason;
 
     @Column(name = "reviewed_by", length = 150)
     private String reviewedBy;
 
     @Column(name = "reviewed_at")
     private LocalDateTime reviewedAt;
 
     @Column(name = "password_hash", length = 255, nullable = false)
     private String passwordHash;

     @Column(name = "ip_address", length = 45)
     private String ipAddress;
 
     @Column(name = "created_at", nullable = false, updatable = false)
     private LocalDateTime createdAt;
 
     @Column(name = "updated_at", nullable = false)
     private LocalDateTime updatedAt;
 
     public RegistrationRequest() {}
 
     public RegistrationRequest(Long id, String email, String passwordHash, RegistrationStatus status, String rejectionReason, String reviewedBy, LocalDateTime reviewedAt, String ipAddress, LocalDateTime createdAt, LocalDateTime updatedAt) {
         this.id = id;
         this.email = email;
         this.passwordHash = passwordHash;
         this.status = status;
         this.rejectionReason = rejectionReason;
         this.reviewedBy = reviewedBy;
         this.reviewedAt = reviewedAt;
         this.ipAddress = ipAddress;
         this.createdAt = createdAt;
         this.updatedAt = updatedAt;
     }
 
     public static RegistrationRequestBuilder builder() {
         return new RegistrationRequestBuilder();
     }
 
     public static class RegistrationRequestBuilder {
         private String email;
         private String passwordHash;
         private RegistrationStatus status = RegistrationStatus.PENDING;
         private String ipAddress;

         public RegistrationRequestBuilder email(String email) { this.email = email; return this; }
         public RegistrationRequestBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
         public RegistrationRequestBuilder status(RegistrationStatus status) { this.status = status; return this; }
         public RegistrationRequestBuilder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
         public RegistrationRequest build() {
             RegistrationRequest r = new RegistrationRequest();
             r.setEmail(email);
             r.setPasswordHash(passwordHash);
             r.setStatus(status);
             r.setIpAddress(ipAddress);
             return r;
         }
     }
 
     @PrePersist
     protected void onCreate() {
         this.createdAt = LocalDateTime.now();
         this.updatedAt = LocalDateTime.now();
     }
 
     @PreUpdate
     protected void onUpdate() {
         this.updatedAt = LocalDateTime.now();
     }
 
     // Getters and Setters
     public Long getId() { return id; }
     public void setId(Long id) { this.id = id; }
     public String getEmail() { return email; }
     public void setEmail(String email) { this.email = email; }
     public String getPasswordHash() { return passwordHash; }
     public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
     public RegistrationStatus getStatus() { return status; }
     public void setStatus(RegistrationStatus status) { this.status = status; }
     public String getRejectionReason() { return rejectionReason; }
     public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
     public String getReviewedBy() { return reviewedBy; }
     public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
     public LocalDateTime getReviewedAt() { return reviewedAt; }
     public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
     public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
     public LocalDateTime getCreatedAt() { return createdAt; }
     public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
     public LocalDateTime getUpdatedAt() { return updatedAt; }
     public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
 }

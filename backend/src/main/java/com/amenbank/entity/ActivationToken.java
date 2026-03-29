package com.amenbank.entity;
 
 import jakarta.persistence.*;
 import java.time.LocalDateTime;
 
 @Entity
 @Table(name = "activation_tokens")
 public class ActivationToken {
 
     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long id;
 
     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "user_id", nullable = false)
     private User user;
 
     @Column(nullable = false, unique = true, length = 255)
     private String token;
 
     @Column(name = "expires_at", nullable = false)
     private LocalDateTime expiresAt;
 
     @Column(nullable = false)
     private Boolean used = false;
 
     @Column(name = "used_at")
     private LocalDateTime usedAt;
 
     @Column(name = "created_at", nullable = false, updatable = false)
     private LocalDateTime createdAt;
 
     public ActivationToken() {}
 
     public static ActivationTokenBuilder builder() {
         return new ActivationTokenBuilder();
     }
 
     public static class ActivationTokenBuilder {
         private User user;
         private String token;
         private LocalDateTime expiresAt;
 
         public ActivationTokenBuilder user(User user) { this.user = user; return this; }
         public ActivationTokenBuilder token(String token) { this.token = token; return this; }
         public ActivationTokenBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
         public ActivationToken build() {
             ActivationToken t = new ActivationToken();
             t.setUser(user);
             t.setToken(token);
             t.setExpiresAt(expiresAt);
             return t;
         }
     }
 
     @PrePersist
     protected void onCreate() {
         this.createdAt = LocalDateTime.now();
     }
 
     public boolean isExpired() {
         return expiresAt.isBefore(LocalDateTime.now());
     }
 
     public boolean isValid() {
         return !used && !isExpired();
     }
 
     // Getters and Setters
     public Long getId() { return id; }
     public void setId(Long id) { this.id = id; }
     public User getUser() { return user; }
     public void setUser(User user) { this.user = user; }
     public String getToken() { return token; }
     public void setToken(String token) { this.token = token; }
     public LocalDateTime getExpiresAt() { return expiresAt; }
     public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
     public Boolean getUsed() { return used; }
     public void setUsed(Boolean used) { this.used = used; }
     public LocalDateTime getUsedAt() { return usedAt; }
     public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
     public LocalDateTime getCreatedAt() { return createdAt; }
     public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
 }

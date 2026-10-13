package com.campusfix.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens", indexes = @Index(name = "idx_password_reset_token", columnList = "token", unique = true))
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 128) private String token;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false) private LocalDateTime expiresAt;
    public PasswordResetToken() {}
    public PasswordResetToken(String token, User user, LocalDateTime expiresAt) { this.token=token; this.user=user; this.expiresAt=expiresAt; }
    public Long getId(){return id;} public String getToken(){return token;} public User getUser(){return user;} public LocalDateTime getExpiresAt(){return expiresAt;}
}

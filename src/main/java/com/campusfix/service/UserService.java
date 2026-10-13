package com.campusfix.service;

import com.campusfix.model.Role;
import com.campusfix.model.User;
import com.campusfix.repository.UserRepository;
import com.campusfix.repository.PasswordResetTokenRepository;
import com.campusfix.model.PasswordResetToken;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository resetTokens;
    private final JavaMailSender mailSender;
    @Value("${campusfix.mail.from:}") private String mailFrom;
    @Value("${campusfix.frontend-url:http://localhost:5173}") private String frontendUrl;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordResetTokenRepository resetTokens, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.resetTokens = resetTokens;
        this.mailSender = mailSender;
    }

    public User register(String username, String rawPassword, String fullName, String email, Role role, String department) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (rawPassword == null || rawPassword.isBlank()) throw new IllegalArgumentException("Password is required");
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setDepartment(department);
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User getByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found: " + username));
    }


    public User updateProfile(User user, String username, String fullName, String email, String department) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (email != null && !email.isBlank() && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setDepartment(department);
        return userRepository.save(user);
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public User updateStaff(Long id, String fullName, String email, String department) {
        User staff = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Staff not found."));
        if (staff.getRole() != Role.STAFF) throw new IllegalArgumentException("User is not Staff.");
        if (email != null && !email.isBlank() && !email.equals(staff.getEmail()) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        staff.setFullName(fullName);
        staff.setEmail(email);
        staff.setDepartment(department);
        return userRepository.save(staff);
    }

    public User deactivateStaff(Long id) {
        User staff = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Staff not found."));
        if (staff.getRole() != Role.STAFF) throw new IllegalArgumentException("User is not Staff.");
        staff.setEnabled(false);
        return userRepository.save(staff);
    }


    public void requestPasswordReset(String email) {
        if (mailFrom == null || mailFrom.isBlank()) throw new IllegalStateException("Password recovery email is not configured. Set CAMPUSFIX_MAIL_FROM and SMTP settings.");
        if (email == null || email.isBlank()) return;
        userRepository.findAll().stream().filter(u -> email.equalsIgnoreCase(u.getEmail()) && u.isEnabled()).findFirst().ifPresent(user -> {
            resetTokens.deleteByUser(user);
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            resetTokens.save(new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(30)));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom); message.setTo(user.getEmail());
            message.setSubject("Reset your CampusFix password");
            message.setText("A password reset was requested for your CampusFix account. Open " + frontendUrl + " and choose Forgot Password, then enter this reset token:\n\n" + token + "\n\nThis token expires in 30 minutes. If you did not request this, ignore this email.");
            mailSender.send(message);
        });
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Reset token is required.");
        if (newPassword == null || newPassword.length() < 8) throw new IllegalArgumentException("Password must be at least 8 characters.");
        PasswordResetToken reset = resetTokens.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) { resetTokens.delete(reset); throw new IllegalArgumentException("Invalid or expired reset token."); }
        User user = reset.getUser(); user.setPassword(passwordEncoder.encode(newPassword)); userRepository.save(user); resetTokens.deleteByUser(user);
    }

    public void deleteInactiveStaff(Long id, boolean hasAssignedComplaints, boolean hasUpdates, boolean hasNotifications) {
        User staff = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Staff not found."));
        if (staff.getRole() != Role.STAFF) throw new IllegalArgumentException("User is not Staff.");
        if (staff.isEnabled()) throw new IllegalArgumentException("Deactivate the staff account before deleting it.");
        if (hasAssignedComplaints || hasUpdates || hasNotifications) throw new IllegalArgumentException("This staff account has linked complaints, updates, or notifications. Keep it deactivated to preserve project history.");
        resetTokens.deleteByUser(staff);
        userRepository.delete(staff);
    }

    public User save(User user) { return userRepository.save(user); }

    public List<User> findAllStudents() {
        return userRepository.findByRole(Role.STUDENT);
    }

    public List<User> findAllAdmins() {
        return userRepository.findByRole(Role.ADMIN);
    }

    public List<User> findStaff() {
        return userRepository.findByRole(Role.STAFF);
    }
}

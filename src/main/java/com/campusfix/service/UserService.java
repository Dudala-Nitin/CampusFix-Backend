package com.campusfix.service;

import com.campusfix.model.Role;
import com.campusfix.model.User;
import com.campusfix.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
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

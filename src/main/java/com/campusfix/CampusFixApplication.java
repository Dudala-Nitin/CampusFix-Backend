package com.campusfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campusfix.model.Role;
import com.campusfix.model.User;
import com.campusfix.repository.UserRepository;

@SpringBootApplication
public class CampusFixApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFixApplication.class, args);
    }

    /**
     * Seeds one default account per role on first run so the app is usable
     * immediately after setup. Passwords are the same as the username, hashed
     * with BCrypt. Change or remove this in a real deployment.
     */
    @Bean
    CommandLineRunner seedDefaultUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            seedIfMissing(userRepository, passwordEncoder, "admin", "admin@campusfix.edu", Role.ADMIN, null);
            seedIfMissing(userRepository, passwordEncoder, "staff", "staff@campusfix.edu", Role.STAFF, "General Maintenance");
            seedIfMissing(userRepository, passwordEncoder, "student", "student@campusfix.edu", Role.STUDENT, null);
        };
    }

    private void seedIfMissing(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                String username, String email, Role role, String department) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(username));
            user.setFullName(username.substring(0, 1).toUpperCase() + username.substring(1) + " Account");
            user.setEmail(email);
            user.setRole(role);
            user.setDepartment(department);
            userRepository.save(user);
        }
    }
}

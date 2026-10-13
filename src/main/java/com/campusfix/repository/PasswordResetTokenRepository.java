package com.campusfix.repository;
import com.campusfix.model.PasswordResetToken;
import com.campusfix.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
 Optional<PasswordResetToken> findByToken(String token);
 @Transactional void deleteByUser(User user);
}

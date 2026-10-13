package com.campusfix.repository;

import com.campusfix.model.Notification;
import com.campusfix.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    long countByUserAndIsReadFalse(User user);
    Optional<Notification> findByIdAndUser(Long id, User user);
    boolean existsByUser(User user);
}

package com.campusfix.service;

import com.campusfix.model.Notification;
import com.campusfix.model.User;
import com.campusfix.repository.NotificationRepository;
import com.campusfix.repository.UserRepository;
import com.campusfix.model.Role;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void notify(User user, String message, Long relatedComplaintId) {
        notificationRepository.save(new Notification(user, message, relatedComplaintId));
    }

    public void notifyRole(Role role, String message, Long relatedComplaintId) {
        userRepository.findByRole(role).forEach(user -> notify(user, message, relatedComplaintId));
    }

    public List<Notification> getForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    public void deleteForUser(Long id, User user) {
        Notification n = notificationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        notificationRepository.delete(n);
    }

    public void markAllRead(User user) {
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }
}

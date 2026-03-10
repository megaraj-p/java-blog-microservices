package com.enterprise.blog.notification.repository;

import com.enterprise.blog.notification.entity.NotificationLog;
import com.enterprise.blog.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient);

    long countByStatus(NotificationStatus status);
}

package com.kailei.demo.controller;

import com.kailei.demo.entity.NotificationEntity;
import com.kailei.demo.repository.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<NotificationEntity> list(@RequestParam String userId,
                                         @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findUnreadByUserId(userId);
        }
        return notificationRepository.findByUserId(userId);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationEntity markRead(@PathVariable String notificationId,
                                       @RequestParam String userId) {
        return notificationRepository.markRead(notificationId, userId);
    }
}

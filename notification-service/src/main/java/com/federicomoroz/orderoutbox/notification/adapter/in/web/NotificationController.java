package com.federicomoroz.orderoutbox.notification.adapter.in.web;

import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manual-verification endpoint, not a core use case (see checklist step 3 in the plan/README) —
 * reads directly through the {@code NotificationRepository} outbound port rather than through an
 * application service. A deliberate CQRS-lite shortcut: this is a plain read with no business
 * logic, so introducing a use-case interface here would be ceremony without payoff.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<NotificationResponse> listNotifications() {
        return notificationRepository.findAll().stream().map(NotificationResponse::from).toList();
    }
}

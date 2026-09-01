package com.federicomoroz.orderoutbox.notification.application.port.out;

import com.federicomoroz.orderoutbox.notification.domain.Notification;

import java.util.List;

public interface NotificationRepository {

    void save(Notification notification);

    List<Notification> findAll();
}

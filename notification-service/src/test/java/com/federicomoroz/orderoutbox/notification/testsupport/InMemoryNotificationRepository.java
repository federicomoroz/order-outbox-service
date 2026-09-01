package com.federicomoroz.orderoutbox.notification.testsupport;

import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.domain.Notification;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryNotificationRepository implements NotificationRepository {

    private final List<Notification> store = new ArrayList<>();

    @Override
    public void save(Notification notification) {
        store.add(notification);
    }

    @Override
    public List<Notification> findAll() {
        return List.copyOf(store);
    }

    public int size() {
        return store.size();
    }
}

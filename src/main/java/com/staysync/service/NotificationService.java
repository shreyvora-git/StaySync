package com.staysync.service;

import com.staysync.dao.NotificationDao;
import com.staysync.model.Notification;
import com.staysync.security.CurrentUser;
import com.staysync.security.Session;

import java.util.List;
import java.util.Objects;

/**
 * Service managing user alert notifications and unread badges.
 * Enforces session authentication and strict recipient security boundaries.
 */
public class NotificationService {

    private final NotificationDao notificationDao;

    public NotificationService(NotificationDao notificationDao) {
        this.notificationDao = notificationDao != null ? notificationDao : new NotificationDao();
    }

    public NotificationService() {
        this(new NotificationDao());
    }

    /**
     * Retrieves recent notifications for the authenticated session user.
     */
    public List<Notification> getMyNotifications(int limit) {
        CurrentUser user = Session.requireCurrentUser();
        return notificationDao.findByUserId(user.userId(), limit);
    }

    /**
     * Counts unread notifications for the authenticated session user.
     */
    public int getUnreadCount() {
        CurrentUser user = Session.requireCurrentUser();
        return notificationDao.countUnreadByUserId(user.userId());
    }

    /**
     * Marks a specific notification as read, ensuring it belongs to the current user.
     */
    public boolean markAsRead(long notificationId) {
        CurrentUser user = Session.requireCurrentUser();
        return notificationDao.markAsRead(notificationId, user.userId());
    }

    /**
     * Marks all unread notifications for the authenticated user as read.
     */
    public int markAllAsRead() {
        CurrentUser user = Session.requireCurrentUser();
        return notificationDao.markAllAsRead(user.userId());
    }
}

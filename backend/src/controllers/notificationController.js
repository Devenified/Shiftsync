const Notification = require('../models/Notification');

// GET /api/notifications
exports.getNotifications = async (req, res) => {
  try {
    const { limit = 50, unreadOnly } = req.query;
    const filter = { userId: req.user.id };
    if (String(unreadOnly) === 'true') {
      filter.read = false;
    }

    const notifications = await Notification.find(filter)
      .sort({ createdAt: -1 })
      .limit(Math.min(Number(limit) || 50, 200));

    return res.json({ notifications });
  } catch (err) {
    console.error('Get notifications error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/notifications/unread-count
exports.unreadCount = async (req, res) => {
  try {
    const count = await Notification.countDocuments({
      userId: req.user.id,
      read: false
    });
    return res.json({ count });
  } catch (err) {
    console.error('Unread count error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/notifications/:id/read
exports.markAsRead = async (req, res) => {
  try {
    const notif = await Notification.findOneAndUpdate(
      { _id: req.params.id, userId: req.user.id },
      { read: true },
      { returnDocument: 'after' }
    );
    if (!notif) {
      return res.status(404).json({ message: 'Notification not found' });
    }
    return res.json({ message: 'Notification marked as read', notification: notif });
  } catch (err) {
    console.error('Mark read error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/notifications/mark-all-read
exports.markAllAsRead = async (req, res) => {
  try {
    const result = await Notification.updateMany(
      { userId: req.user.id, read: false },
      { read: true }
    );
    return res.json({
      message: 'All notifications marked as read',
      modified: result.modifiedCount || 0
    });
  } catch (err) {
    console.error('Mark all read error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// DELETE /api/notifications/:id
exports.deleteNotification = async (req, res) => {
  try {
    const deleted = await Notification.findOneAndDelete({
      _id: req.params.id,
      userId: req.user.id
    });
    if (!deleted) {
      return res.status(404).json({ message: 'Notification not found' });
    }
    return res.json({ message: 'Notification deleted' });
  } catch (err) {
    console.error('Delete notification error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/notifications (internal helper - announcements by employer)
exports.createAnnouncement = async (req, res) => {
  try {
    const { targetUserIds, message } = req.body;
    if (!message || !Array.isArray(targetUserIds) || targetUserIds.length === 0) {
      return res.status(400).json({ message: 'Missing message or targetUserIds' });
    }
    const docs = targetUserIds.map((id) => ({
      userId: id,
      message,
      type: 'announcement'
    }));
    const created = await Notification.insertMany(docs);
    return res.status(201).json({ message: 'Announcement sent', count: created.length });
  } catch (err) {
    console.error('Create announcement error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

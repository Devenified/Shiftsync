const Notification = require('../models/Notification');

exports.getNotifications = async (req, res) => {
  try {
    const notifications = await Notification.find({ userId: req.user.id })
      .sort({ createdAt: -1 })
      .limit(20);
    return res.json({ notifications });
  } catch (err) {
    console.error('Get notifications error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

exports.markAsRead = async (req, res) => {
  try {
    await Notification.findByIdAndUpdate(req.params.id, { read: true });
    return res.json({ message: 'Notification marked as read' });
  } catch (err) {
    console.error('Mark read error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

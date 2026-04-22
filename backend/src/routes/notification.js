const express = require('express');
const router = express.Router();
const notificationController = require('../controllers/notificationController');
const { authMiddleware, requireRole } = require('../lib/jwt');

router.get('/', authMiddleware, notificationController.getNotifications);
router.get('/unread-count', authMiddleware, notificationController.unreadCount);
router.patch('/mark-all-read', authMiddleware, notificationController.markAllAsRead);
router.patch('/:id/read', authMiddleware, notificationController.markAsRead);
router.delete('/:id', authMiddleware, notificationController.deleteNotification);
router.post(
  '/announce',
  authMiddleware,
  requireRole('employer'),
  notificationController.createAnnouncement
);

module.exports = router;

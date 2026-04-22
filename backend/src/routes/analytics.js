const express = require('express');
const router = express.Router();

const analyticsController = require('../controllers/analyticsController');
const { authMiddleware, requireRole } = require('../lib/jwt');

router.get(
  '/employer',
  authMiddleware,
  requireRole('employer'),
  analyticsController.employerAnalytics
);

router.get(
  '/worker',
  authMiddleware,
  requireRole('worker'),
  analyticsController.workerAnalytics
);

module.exports = router;

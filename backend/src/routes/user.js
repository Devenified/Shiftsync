const express = require('express');
const router = express.Router();

const userController = require('../controllers/userController');
const { authMiddleware, requireRole } = require('../lib/jwt');

// Signup: create new user (public)
router.post('/signup', userController.signup);

// Login: email + password (public)
router.post('/login', userController.login);
router.post('/login-worker', userController.loginWorker);
router.post('/login-employer', userController.loginEmployer);

// Logout: clear session (protected)
router.post('/logout', authMiddleware, userController.logout);

// Get current user profile (protected with JWT)
router.get('/me', authMiddleware, userController.getProfile);
router.patch('/me', authMiddleware, userController.updateProfile);

// Role-based dashboard routes
router.get(
  '/worker-dashboard',
  authMiddleware,
  requireRole('worker'),
  userController.getWorkerDashboard
);
router.get(
  '/employer-dashboard',
  authMiddleware,
  requireRole('employer'),
  userController.getEmployerDashboard
);
router.get(
  '/workers/search',
  authMiddleware,
  requireRole('employer'),
  userController.searchWorkers
);

module.exports = router;


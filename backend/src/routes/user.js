const express = require('express');
const router = express.Router();

const userController = require('../controllers/userController');
const { authMiddleware } = require('../lib/jwt');

// Signup: create new user (public)
router.post('/signup', userController.signup);

// Login: email + password (public)
router.post('/login', userController.login);

// Get current user profile (protected with JWT)
router.get('/me', authMiddleware, userController.getProfile);

module.exports = router;


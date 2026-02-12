const express = require('express');
const router = express.Router();

const userController = require('../controllers/userController');

// Signup: create new user
router.post('/signup', userController.signup);

// Login: email + password
router.post('/login', userController.login);

module.exports = router;


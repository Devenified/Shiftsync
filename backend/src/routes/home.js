const express = require('express');
const router = express.Router();

const homeController = require('../controllers/homeController');

// Root route
router.get('/', homeController.getHome);

// Simple dummy route to test connection
router.get('/health', homeController.healthCheck);

module.exports = router;


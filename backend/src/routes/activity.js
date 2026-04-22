const express = require('express');
const router = express.Router();
const activityController = require('../controllers/activityController');
const { authMiddleware } = require('../lib/jwt');

router.get('/', authMiddleware, activityController.getFeed);

module.exports = router;

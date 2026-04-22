const express = require('express');
const router = express.Router();
const swapController = require('../controllers/swapController');
const { authMiddleware } = require('../lib/jwt');

router.post('/request', authMiddleware, swapController.requestSwap);
router.get('/received', authMiddleware, swapController.getReceivedSwaps);
router.patch('/:id/respond', authMiddleware, swapController.respondToSwap);

module.exports = router;

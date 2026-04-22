const express = require('express');
const router = express.Router();
const leaveController = require('../controllers/leaveController');
const { authMiddleware, requireRole } = require('../lib/jwt');

router.get('/balance', authMiddleware, leaveController.getLeaveBalance);
router.post('/request', authMiddleware, leaveController.requestLeave);
router.get('/my', authMiddleware, leaveController.getMyLeaves);
router.get('/all', authMiddleware, requireRole('employer'), leaveController.getAllLeaves);
router.patch('/:id/approve', authMiddleware, requireRole('employer'), leaveController.approveLeave);

module.exports = router;

const express = require('express');
const router = express.Router();

const shiftController = require('../controllers/shiftController');
const { authMiddleware, requireRole } = require('../lib/jwt');

// Worker routes
router.get('/open', authMiddleware, requireRole('worker'), shiftController.listOpenShifts);
router.post('/:id/apply', authMiddleware, requireRole('worker'), shiftController.applyToShift);
router.get('/worker/my', authMiddleware, requireRole('worker'), shiftController.listWorkerShifts);

// Employer routes
router.post('/', authMiddleware, requireRole('employer'), shiftController.createShift);
router.get('/employer/my', authMiddleware, requireRole('employer'), shiftController.listEmployerShifts);
router.patch(
  '/:id/applications/:workerId',
  authMiddleware,
  requireRole('employer'),
  shiftController.reviewApplication
);
router.patch('/:id/complete', authMiddleware, requireRole('employer'), shiftController.completeShift);

module.exports = router;


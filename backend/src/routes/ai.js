const express = require('express');
const router = express.Router();

const aiController = require('../controllers/aiController');
const { authMiddleware, requireRole } = require('../lib/jwt');

// General AI advisor - available to both workers and employers
router.post('/ask-skill-advisor', authMiddleware, aiController.askSkillAdvisor);
router.post('/ask-skill-advisor-stream', authMiddleware, aiController.askSkillAdvisorStream);

// Worker-specific endpoints
router.post('/skill-assessment', authMiddleware, requireRole('worker'), aiController.skillAssessment);
router.post('/job-recommendations', authMiddleware, requireRole('worker'), aiController.jobRecommendations);

// Employer-specific AI helper
router.post('/employer-helper', authMiddleware, requireRole('employer'), aiController.employerHelper);

module.exports = router;

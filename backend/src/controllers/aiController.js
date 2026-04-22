const { GoogleGenerativeAI } = require('@google/generative-ai');

let _genAI = null;
function getGenAI() {
  if (!_genAI) {
    _genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
  }
  return _genAI;
}

function getModel() {
  return getGenAI().getGenerativeModel({ model: 'gemini-2.0-flash' });
}

const SYSTEM_PROMPT = `You are an intelligent Skill Advisor Assistant for ShiftSync, a worker marketplace platform.

Your role:
1. Assess and improve workers' professional skills
2. Identify skill gaps and recommend learning paths
3. Provide career guidance and job recommendations
4. Suggest strategies to increase earnings
5. Offer tips for professional growth

Guidelines:
- Be encouraging and supportive
- Provide practical, actionable advice
- Focus on skills relevant to gig work, labor, and shift-based jobs
- Suggest specific courses, certifications, or training when appropriate
- Keep responses concise but informative (2-3 paragraphs max)
- Use simple language easy for everyone to understand
- Always maintain a professional and friendly tone

When users ask about skills:
- Assess their current level
- Recommend next steps
- Suggest ways to demonstrate skills to employers
- Provide tips for higher-paying opportunities`;

const EMPLOYER_SYSTEM_PROMPT = `You are a smart Hiring & Management Assistant for ShiftSync, an employer workforce platform.

Your role is to help employers:
1. Write effective shift/job descriptions
2. Decide fair wages for different roles
3. Tips on managing shift workers and teams
4. How to find and retain good workers
5. Legal tips about hiring and labor regulations
6. Scheduling and workforce optimization advice

Guidelines:
- Be practical and business-focused
- Keep answers concise (2-3 paragraphs)
- Use simple, clear language
- Give actionable advice employers can use right away
- Be helpful with hiring, scheduling, and worker management questions`;

const conversationMemories = new Map();
const MEMORY_TTL_MS = 30 * 60 * 1000;

function getMemory(userId) {
  const existing = conversationMemories.get(userId);
  if (existing && Date.now() - existing.lastAccess < MEMORY_TTL_MS) {
    existing.lastAccess = Date.now();
    return existing.history;
  }
  const history = [];
  conversationMemories.set(userId, { history, lastAccess: Date.now() });
  return history;
}

function trimMemory(history, maxPairs = 8) {
  while (history.length > maxPairs * 2) {
    history.shift();
  }
}

function buildPrompt(systemPrompt, history, question) {
  let prompt = systemPrompt + '\n\n';
  for (const entry of history) {
    prompt += entry.role === 'user'
      ? `User: ${entry.text}\n`
      : `Assistant: ${entry.text}\n`;
  }
  prompt += `User: ${question}\n\nAssistant:`;
  return prompt;
}

function handleAIError(err, res) {
  const msg = err.message || '';
  console.error('AI error:', msg.substring(0, 300));

  if (msg.includes('429') || msg.includes('quota') || msg.includes('Too Many Requests')) {
    return res.status(429).json({
      message: 'AI quota exceeded. The free tier daily limit has been reached. Please try again later or upgrade the API plan.',
      answer: 'I\'m temporarily unavailable due to high usage. Please try again in a few minutes!',
    });
  }
  if (msg.includes('API key') || msg.includes('API_KEY_INVALID')) {
    return res.status(500).json({ message: 'AI service not configured. Please set a valid GEMINI_API_KEY.' });
  }
  if (msg.includes('404') || msg.includes('not found')) {
    return res.status(500).json({ message: 'AI model not available. Please check the model configuration.' });
  }
  return res.status(500).json({ message: 'Error generating AI response. Please try again.' });
}

/**
 * POST /api/ai/ask-skill-advisor
 */
exports.askSkillAdvisor = async (req, res) => {
  try {
    const { question } = req.body;
    if (!question || typeof question !== 'string' || question.trim().length === 0) {
      return res.status(400).json({ message: 'Question is required' });
    }

    const userId = req.user.id;
    const history = getMemory(userId);
    const prompt = buildPrompt(SYSTEM_PROMPT, history, question.trim());

    const model = getModel();
    const result = await model.generateContent(prompt);
    const answer = result.response.text();

    history.push({ role: 'user', text: question.trim() });
    history.push({ role: 'assistant', text: answer });
    trimMemory(history);

    console.log(`[AI] User ${userId}: ${question.substring(0, 80)}...`);

    return res.json({
      message: 'Response generated successfully',
      question,
      answer,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    return handleAIError(err, res);
  }
};

/**
 * POST /api/ai/ask-skill-advisor-stream
 */
exports.askSkillAdvisorStream = async (req, res) => {
  try {
    const { question } = req.body;
    if (!question || typeof question !== 'string' || question.trim().length === 0) {
      return res.status(400).json({ message: 'Question is required' });
    }

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    const userId = req.user.id;
    const history = getMemory(userId);
    const prompt = buildPrompt(SYSTEM_PROMPT, history, question.trim());

    const model = getModel();
    const result = await model.generateContentStream(prompt);

    let fullResponse = '';
    for await (const chunk of result.stream) {
      const text = chunk.text();
      if (text) {
        fullResponse += text;
        res.write(`data: ${JSON.stringify({ chunk: text })}\n\n`);
      }
    }

    history.push({ role: 'user', text: question.trim() });
    history.push({ role: 'assistant', text: fullResponse });
    trimMemory(history);

    res.write(`data: ${JSON.stringify({ done: true, answer: fullResponse })}\n\n`);
    res.end();
  } catch (err) {
    console.error('Stream error:', err.message?.substring(0, 200));
    if (!res.headersSent) {
      return handleAIError(err, res);
    }
    res.end();
  }
};

/**
 * POST /api/ai/skill-assessment
 */
exports.skillAssessment = async (req, res) => {
  try {
    const { currentSkills, targetSkills, experience } = req.body;
    if (!currentSkills || !Array.isArray(currentSkills) || currentSkills.length === 0) {
      return res.status(400).json({ message: 'Current skills must be provided as an array' });
    }

    const prompt = `${SYSTEM_PROMPT}

Provide a detailed skill assessment for a worker:
- Current Skills: ${currentSkills.join(', ')}
- Target Skills: ${targetSkills ? targetSkills.join(', ') : 'Not specified'}
- Experience: ${experience ? experience + ' years' : 'Not specified'}

Please provide:
1. Assessment of current skill level
2. Top 3 skills to develop next
3. Recommended training/certification resources
4. Estimated timeline for skill development
5. Potential salary/earning increase with these skills`;

    const model = getModel();
    const result = await model.generateContent(prompt);
    const assessment = result.response.text();

    return res.json({
      message: 'Skill assessment completed',
      assessment,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    return handleAIError(err, res);
  }
};

/**
 * POST /api/ai/job-recommendations
 */
exports.jobRecommendations = async (req, res) => {
  try {
    const { skills, location, workType } = req.body;
    if (!skills || !Array.isArray(skills) || skills.length === 0) {
      return res.status(400).json({ message: 'Skills must be provided as a non-empty array' });
    }

    const prompt = `${SYSTEM_PROMPT}

Based on this worker profile, recommend the best opportunities:
- Skills: ${skills.join(', ')}
- Location: ${location || 'Not specified'}
- Preferred Work Type: ${workType || 'Open to any work type'}

Please provide:
1. Top 5 job types that match these skills
2. Estimated hourly/daily rates
3. Most valuable skills for earning more
4. Tips to stand out
5. Certifications that could increase earnings`;

    const model = getModel();
    const result = await model.generateContent(prompt);
    const recommendations = result.response.text();

    return res.json({
      message: 'Job recommendations generated',
      recommendations,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    return handleAIError(err, res);
  }
};

/**
 * POST /api/ai/employer-helper
 */
exports.employerHelper = async (req, res) => {
  try {
    const { question } = req.body;
    if (!question || typeof question !== 'string' || question.trim().length === 0) {
      return res.status(400).json({ message: 'Question is required' });
    }

    const userId = req.user.id;
    const history = getMemory(userId);
    const prompt = buildPrompt(EMPLOYER_SYSTEM_PROMPT, history, question.trim());

    const model = getModel();
    const result = await model.generateContent(prompt);
    const answer = result.response.text();

    history.push({ role: 'user', text: question.trim() });
    history.push({ role: 'assistant', text: answer });
    trimMemory(history);

    return res.json({
      message: 'Response generated successfully',
      question,
      answer,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    return handleAIError(err, res);
  }
};

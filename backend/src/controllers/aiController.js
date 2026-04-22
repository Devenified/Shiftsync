const { ChatGoogleGenerativeAI } = require('@langchain/google-genai');
const { ChatPromptTemplate, MessagesPlaceholder } = require('@langchain/core/prompts');
const { HumanMessage, AIMessage } = require('@langchain/core/messages');

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
const MAX_HISTORY_PAIRS = 8;

let chatModel = null;

function getModel() {
  if (chatModel) {
    return chatModel;
  }

  if (!process.env.GEMINI_API_KEY) {
    throw new Error('GEMINI_API_KEY is missing');
  }

  chatModel = new ChatGoogleGenerativeAI({
    apiKey: process.env.GEMINI_API_KEY,
    model: 'gemini-2.0-flash',
    temperature: 0.4,
  });

  return chatModel;
}

function getConversationKey(userId, assistantType) {
  return `${assistantType}:${userId}`;
}

function getMemory(userId, assistantType) {
  const key = getConversationKey(userId, assistantType);
  const existing = conversationMemories.get(key);

  if (existing && Date.now() - existing.lastAccess < MEMORY_TTL_MS) {
    existing.lastAccess = Date.now();
    return existing.history;
  }

  const history = [];
  conversationMemories.set(key, { history, lastAccess: Date.now() });
  return history;
}

function trimMemory(history, maxPairs = MAX_HISTORY_PAIRS) {
  while (history.length > maxPairs * 2) {
    history.shift();
  }
}

function touchMemory(userId, assistantType, history) {
  conversationMemories.set(getConversationKey(userId, assistantType), {
    history,
    lastAccess: Date.now(),
  });
}

function createChain(systemPrompt) {
  const prompt = ChatPromptTemplate.fromMessages([
    ['system', systemPrompt],
    new MessagesPlaceholder('history'),
    ['human', '{question}'],
  ]);

  return prompt.pipe(getModel());
}

function getTextContent(content) {
  if (typeof content === 'string') {
    return content;
  }

  if (Array.isArray(content)) {
    return content
      .map((part) => {
        if (typeof part === 'string') {
          return part;
        }
        if (part && typeof part.text === 'string') {
          return part.text;
        }
        return '';
      })
      .join('');
  }

  return '';
}

async function runAssistant({ systemPrompt, assistantType, userId, question }) {
  const history = getMemory(userId, assistantType);
  const chain = createChain(systemPrompt);
  const response = await chain.invoke({
    history,
    question,
  });

  const answer = getTextContent(response.content).trim();

  history.push(new HumanMessage(question));
  history.push(new AIMessage(answer));
  trimMemory(history);
  touchMemory(userId, assistantType, history);

  return answer;
}

async function streamAssistant({ systemPrompt, assistantType, userId, question, res }) {
  const history = getMemory(userId, assistantType);
  const chain = createChain(systemPrompt);
  const stream = await chain.stream({
    history,
    question,
  });

  let fullResponse = '';

  for await (const chunk of stream) {
    const text = getTextContent(chunk.content);
    if (!text) {
      continue;
    }

    fullResponse += text;
    res.write(`data: ${JSON.stringify({ chunk: text })}\n\n`);
  }

  const answer = fullResponse.trim();
  history.push(new HumanMessage(question));
  history.push(new AIMessage(answer));
  trimMemory(history);
  touchMemory(userId, assistantType, history);

  return answer;
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
  if (msg.includes('API key') || msg.includes('API_KEY_INVALID') || msg.includes('missing')) {
    return res.status(500).json({ message: 'AI service not configured. Please set a valid GEMINI_API_KEY.' });
  }
  if (msg.includes('404') || msg.includes('not found')) {
    return res.status(500).json({ message: 'AI model not available. Please check the model configuration.' });
  }
  return res.status(500).json({ message: 'Error generating AI response. Please try again.' });
}

function validateQuestion(req, res) {
  const { question } = req.body;

  if (!question || typeof question !== 'string' || question.trim().length === 0) {
    res.status(400).json({ message: 'Question is required' });
    return null;
  }

  return question.trim();
}

/**
 * POST /api/ai/ask-skill-advisor
 */
exports.askSkillAdvisor = async (req, res) => {
  try {
    const question = validateQuestion(req, res);
    if (!question) {
      return;
    }

    const userId = req.user.id;
    const answer = await runAssistant({
      systemPrompt: SYSTEM_PROMPT,
      assistantType: 'worker-skill-advisor',
      userId,
      question,
    });

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
    const question = validateQuestion(req, res);
    if (!question) {
      return;
    }

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    const userId = req.user.id;
    const answer = await streamAssistant({
      systemPrompt: SYSTEM_PROMPT,
      assistantType: 'worker-skill-advisor',
      userId,
      question,
      res,
    });

    res.write(`data: ${JSON.stringify({ done: true, answer })}\n\n`);
    res.end();
  } catch (err) {
    console.error('Stream error:', err.message?.substring(0, 200));
    if (!res.headersSent) {
      return handleAIError(err, res);
    }

    res.write(`data: ${JSON.stringify({ done: true, error: 'Error generating AI response. Please try again.' })}\n\n`);
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

    const question = `Provide a detailed skill assessment for a worker:
- Current Skills: ${currentSkills.join(', ')}
- Target Skills: ${targetSkills ? targetSkills.join(', ') : 'Not specified'}
- Experience: ${experience ? `${experience} years` : 'Not specified'}

Please provide:
1. Assessment of current skill level
2. Top 3 skills to develop next
3. Recommended training/certification resources
4. Estimated timeline for skill development
5. Potential salary/earning increase with these skills`;

    const assessment = await runAssistant({
      systemPrompt: SYSTEM_PROMPT,
      assistantType: 'worker-skill-assessment',
      userId: req.user.id,
      question,
    });

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

    const question = `Based on this worker profile, recommend the best opportunities:
- Skills: ${skills.join(', ')}
- Location: ${location || 'Not specified'}
- Preferred Work Type: ${workType || 'Open to any work type'}

Please provide:
1. Top 5 job types that match these skills
2. Estimated hourly/daily rates
3. Most valuable skills for earning more
4. Tips to stand out
5. Certifications that could increase earnings`;

    const recommendations = await runAssistant({
      systemPrompt: SYSTEM_PROMPT,
      assistantType: 'worker-job-recommendations',
      userId: req.user.id,
      question,
    });

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
    const question = validateQuestion(req, res);
    if (!question) {
      return;
    }

    const answer = await runAssistant({
      systemPrompt: EMPLOYER_SYSTEM_PROMPT,
      assistantType: 'employer-helper',
      userId: req.user.id,
      question,
    });

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

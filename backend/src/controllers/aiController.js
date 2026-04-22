const { ChatGoogleGenerativeAI } = require('@langchain/google-genai');
const { ChatOpenAI } = require('@langchain/openai');
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
let activeProvider = null;
let activeModelName = null;

function detectProvider(key) {
  if (!key) return null;
  const k = String(key).trim();
  if (k.startsWith('sk-') || k.startsWith('sess-')) return 'openai';
  if (k.startsWith('AIza')) return 'gemini';
  const forced = (process.env.AI_PROVIDER || '').toLowerCase();
  if (forced === 'openai' || forced === 'gemini') return forced;
  return 'openai';
}

function resolveKey() {
  return (
    process.env.OPENAI_API_KEY ||
    process.env.GEMINI_API_KEY ||
    process.env.GOOGLE_API_KEY ||
    process.env.AI_API_KEY ||
    ''
  );
}

function getModel() {
  if (chatModel) {
    return chatModel;
  }

  const key = resolveKey();
  if (!key) {
    throw new Error('AI API key is missing (set OPENAI_API_KEY or GEMINI_API_KEY)');
  }

  const provider = detectProvider(key);
  activeProvider = provider;

  if (provider === 'openai') {
    activeModelName = process.env.OPENAI_MODEL || 'gpt-4o-mini';
    chatModel = new ChatOpenAI({
      apiKey: key,
      model: activeModelName,
      temperature: 0.4,
    });
  } else {
    activeModelName = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
    chatModel = new ChatGoogleGenerativeAI({
      apiKey: key,
      model: activeModelName,
      temperature: 0.4,
    });
  }

  console.log(`[AI] Using provider=${activeProvider} model=${activeModelName}`);
  return chatModel;
}

function resetModel() {
  chatModel = null;
  activeProvider = null;
  activeModelName = null;
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

const WORKER_FALLBACKS = [
  'To raise your earnings, focus on one adjacent skill (e.g. power-tools, barista, pos), get one small certification, and keep your response time under 5 minutes. Profiles with a photo, a short bio, and 3 past gigs get ~2x more callbacks.',
  'A great way to stand out: fill out your skills list with specific tools you use, note your availability clearly, and complete your first shifts with perfect attendance. Ratings above 4.5 are what employers filter for.',
  'Try searching shifts with skills close to what you already do and 5-15 km from your area - higher match-rate, less commute. Take a screenshot of reviews and share them with new clients.'
];

const EMPLOYER_FALLBACKS = [
  'Strong shift posts lead with a clear title ("Warehouse packer - evening"), one-line duties, exact location, start/end time, and an honest pay band. Posts with pay ranges get 60%+ more applications.',
  'For quick, reliable hires: set a clear required-skill tag, accept the first two qualified workers with rating >= 4.3, and always confirm the shift the evening before. Consider a small on-time bonus.',
  'Retention tip: send a short thank-you + rating prompt after every completed shift. Workers who get rated 5 once tend to return 3x more often.'
];

function pickFallback(assistantType, question) {
  const source = assistantType && assistantType.startsWith('employer') ? EMPLOYER_FALLBACKS : WORKER_FALLBACKS;
  const idx = Math.abs((question || '').length) % source.length;
  return source[idx];
}

function handleAIError(err, res, opts = {}) {
  const msg = err.message || '';
  console.error('AI error:', msg.substring(0, 300));

  const fallback = opts.fallback ||
    'Our live AI is busy right now. Here\'s a quick tip while it cools down: focus on one clear specialty, keep your profile honest and complete, and communicate quickly - those three habits beat most algorithmic advice.';

  const transient =
    msg.includes('429') ||
    msg.includes('quota') ||
    msg.includes('Too Many Requests') ||
    msg.includes('rate limit') ||
    msg.includes('API key') ||
    msg.includes('API_KEY_INVALID') ||
    msg.includes('invalid_api_key') ||
    msg.includes('Incorrect API key') ||
    msg.includes('401') ||
    msg.includes('403') ||
    msg.includes('missing') ||
    msg.includes('404') ||
    msg.includes('not found') ||
    msg.includes('ECONNRESET') ||
    msg.includes('ENOTFOUND') ||
    msg.includes('ETIMEDOUT');

  if (transient) {
    resetModel();
    return res.status(200).json({
      message: 'AI in fallback mode',
      answer: fallback,
      fallback: true,
    });
  }

  return res.status(200).json({
    message: 'AI fallback (unexpected error)',
    answer: fallback,
    fallback: true,
  });
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
    return handleAIError(err, res, { fallback: pickFallback('worker', req.body.question) });
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
    return handleAIError(err, res, { fallback: pickFallback('worker', 'skill-assessment') });
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
    return handleAIError(err, res, { fallback: pickFallback('worker', 'job-recommendations') });
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
    return handleAIError(err, res, { fallback: pickFallback('employer', req.body.question) });
  }
};

/**
 * GET /api/ai/health - tells client whether live AI is probably available
 */
exports.health = (req, res) => {
  const key = resolveKey();
  const keyPresent = !!key;
  const provider = keyPresent ? detectProvider(key) : null;
  const model = provider === 'openai'
    ? (process.env.OPENAI_MODEL || 'gpt-4o-mini')
    : (process.env.GEMINI_MODEL || 'gemini-2.0-flash');
  return res.json({
    keyPresent,
    provider,
    model,
    features: ['skill-advisor', 'skill-assessment', 'job-recommendations', 'employer-helper'],
  });
};

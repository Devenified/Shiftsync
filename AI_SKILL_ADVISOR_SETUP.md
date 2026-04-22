# 🤖 ShiftSync AI Skill Advisor - Setup & Installation Guide

## ✅ What's Been Implemented

### Backend AI Integration
- ✅ **Google Gemini API Integration** (via @google/generative-ai)
- ✅ **Smart Skill Advisor Endpoint** (/api/ai/ask-skill-advisor)
- ✅ **Skill Assessment Engine** (/api/ai/skill-assessment)
- ✅ **Job Recommendations System** (/api/ai/job-recommendations)
- ✅ **JWT Authentication** on all AI endpoints (workers only)
- ✅ **System Prompt** optimized for worker skill development

### Android UI Implementation
- ✅ **AISkillAdvisorActivity** - Full chat interface
- ✅ **AI Message Model & Adapter** - Chat history management
- ✅ **Message UI** - Different styles for user/bot messages
- ✅ **Quick Action Dialogs** - Skill assessment & job recommendations
- ✅ **Worker Dashboard Integration** - 🤖 AI Advisor button
- ✅ **Database Models** - AIMessage class with timestamps
- ✅ **Color Scheme** - Professional chat UI colors

### Files Created/Modified

**Backend Files:**
```
backend/package.json - Added @google/generative-ai
backend/.env - Added GEMINI_API_KEY
backend/src/controllers/aiController.js - NEW (7 functions)
backend/src/routes/ai.js - NEW (4 endpoints)
backend/src/server.js - Updated to register AI routes
```

**Android Files:**
```
android-app/.../AISkillAdvisorActivity.java - NEW
android-app/.../AIMessage.java - NEW
android-app/.../AIMessageAdapter.java - NEW
android-app/.../WorkerDashboardCleanActivity.java - UPDATED
android-app/.../AndroidManifest.xml - UPDATED
android-app/res/layout/activity_ai_skill_advisor.xml - NEW
android-app/res/layout/item_ai_message_user.xml - NEW
android-app/res/layout/item_ai_message_bot.xml - NEW
android-app/res/layout/dialog_skill_assessment.xml - NEW
android-app/res/layout/dialog_job_recommendations.xml - NEW
android-app/res/values/colors.xml - UPDATED (chat colors)
android-app/res/drawable/bg_input.xml - NEW
android-app/res/drawable/bg_input_rounded.xml - NEW
android-app/res/drawable/bg_button_rounded.xml - NEW
```

---

## 🚀 Setup Instructions

### Backend Setup

1. **Install Gemini AI Package**
   ```bash
   cd backend
   npm install
   ```
   This installs `@google/generative-ai` (already added to package.json)

2. **Verify .env Configuration**
   ```
   PORT=3000
   MONGO_URI=mongodb://localhost:27017/ShiftSync
   HOST=0.0.0.0
   JWT_SECRET=MAD_Project
   GEMINI_API_KEY=AIzaSyC91jGzTwGApUgKId7yzPlMFx06JHH3FPY
   ```

3. **Start Backend**
   ```bash
   npm start
   ```
   You should see: "Server addresses: ..." and "Connected to MongoDB"

### Android Setup

1. **Sync Gradle** (Android Studio)
   - File → Sync Now
   - Wait for build to complete

2. **Run on Emulator/Device**
   - Click Run button or Shift+F10

---

## 💬 How to Use AI Skill Advisor

### User Flow
1. Open app and login as a **Worker**
2. On Worker Dashboard, look for **🤖 AI Advisor** button (in Quick Actions grid)
3. Click to open Chat Interface
4. See welcome message from AI
5. Type your question: e.g., "How can I earn more with my carpentry skills?"
6. Get instant AI-powered response

### AI Skill Advisor Features

**Free-form Questions**
- "What skills are most in-demand?"
- "How much can I earn with electrical work?"
- "How do I improve my rating?"
- "What jobs suit my experience?"

**Structured Assessments** 
- Click "📊 Assess Skills" button
- Enter current skills (comma-separated)
- Optional: target skills, experience level
- Get personalized development plan

**Job Opportunities**
- Click "💼 Job Ideas" button
- Enter your skills
- Optional: location preference, work type
- Get recommendations for high-paying job types

---

## 🔌 API Endpoints

### 1. Ask Skill Advisor
**Endpoint:** `POST /api/ai/ask-skill-advisor`  
**Auth:** Bearer token (worker)

```bash
curl -X POST http://localhost:3000/api/ai/ask-skill-advisor \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question": "What skills pay the most?"}'
```

**Response:**
```json
{
  "message": "Response generated successfully",
  "question": "What skills pay the most?",
  "answer": "Based on market data...",
  "timestamp": "2026-03-25T10:30:00Z"
}
```

### 2. Skill Assessment
**Endpoint:** `POST /api/ai/skill-assessment`  
**Auth:** Bearer token (worker)

```bash
curl -X POST http://localhost:3000/api/ai/skill-assessment \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "currentSkills": ["Carpentry", "Painting"],
    "targetSkills": ["Electrical", "Plumbing"],
    "experience": 5
  }'
```

### 3. Job Recommendations
**Endpoint:** `POST /api/ai/job-recommendations`  
**Auth:** Bearer token (worker)

```bash
curl -X POST http://localhost:3000/api/ai/job-recommendations \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "skills": ["Painting", "Carpentry"],
    "location": "Mumbai",
    "workType": "Part-time"
  }'
```

---

## 🎨 UI Overview

### Activity Layout Structure
```
┌─────────────────────────────────────┐
│        Toolbar (Title + Back)       │
├─────────────────────────────────────┤
│  [Quick Buttons: Assess | Job Ideas]│
├─────────────────────────────────────┤
│                                     │
│    Messages RecyclerView            │
│    (Chat History)                   │
│                                     │
│    - User message (right, orange)   │
│    - Bot message  (left, blue)      │
│    - Loading indicator              │
│                                     │
├─────────────────────────────────────┤
│  [Question Input] [Send Button]     │
└─────────────────────────────────────┘
```

### Message Styling
- **User Messages**: Orange bubbles, right-aligned, white text
- **Bot Messages**: Light blue bubbles, left-aligned, dark text
- **Error Messages**: Red bubbles for errors
- **Timestamps**: Shown on each message (HH:mm format)

---

## 🧪 Testing Scenarios

### Test 1: Basic Q&A
1. Login as worker
2. Open AI Advisor
3. Type: "What skills should I learn first?"
4. Verify: Receive contextual response

### Test 2: Skill Assessment
1. Click "📊 Assess Skills"
2. Enter: Current = "Carpentry, Painting"
3. Enter: Target = "Electrical, Plumbing"
4. Enter: Experience = "5"
5. Click "Analyze"
6. Verify: Get detailed assessment

### Test 3: Job Recommendations
1. Click "💼 Job Ideas"
2. Enter: Skills = "Painting, Carpentry"
3. Enter: Location = "Mumbai"
4. Click "Get Recommendations"
5. Verify: Get job opportunities

### Test 4: Error Handling
1. Try accessing without login → Redirect to login
2. Try as employer → 403 Forbidden error
3. Disconnect network → Connection error message
4. Empty question → Validation error

---

##  Troubleshooting

### Issue: "API service configuration error"
**Solution:** Check `GEMINI_API_KEY` in `.env` file

### Issue: "Too many requests"
**Solution:** Wait a few minutes, API rate limit triggered

### Issue: "Session expired"
**Solution:** Login again, JWT token may have expired

### Issue: "Android won't build"
**Solution:** 
```bash
./gradlew clean build
# or in Android Studio: Build → Clean Project
```

### Issue: Backend won't start
**Solution:**
```bash
# Check MongoDB is running
mongod
# Check .env file exists
# Restart backend: npm start
```

---

## 📦 Dependencies

### Backend
```json
{
  "@google/generative-ai": "^0.3.1",
  "express": "^4.21.2",
  "mongoose": "^9.2.1",
  "jsonwebtoken": "^9.0.3",
  "cors": "^2.8.6",
  "dotenv": "^17.2.4",
  "bcryptjs": "^3.0.3"
}
```

### Android
- Material Components for Android
- RecyclerView for chat history
- CardView for message bubbles
- HttpURLConnection for API calls

---

## 🔐 Security Notes

- ✅ JWT authentication required for all AI endpoints
- ✅ Workers-only access with role validation
- ✅ API key stored in backend `.env` (never exposed to client)
- ✅ Rate limiting recommended in production
- ✅ Input validation on all user inputs
- ✅ Session timeout after 7 days

---

## 🚀 Next Enhancements

1. **Conversation History**
   - Save to database
   - Retrieve previous conversations
   - Export chat as PDF

2. **Voice Integration**
   - Text-to-speech for AI responses
   - Speech-to-text for user input

3. **Success Metrics**
   - Track which advice workers follow
   - Measure earnings increase
   - Show career progress over time

4. **Personalization**
   - Learning based on worker history
   - Customized recommendations
   - Adaptive difficulty levels

5. **Multi-language Support**
   - Hindi, Marathi, Bengali
   - Automatic language detection
   - Translated responses

---

## ✨ Features & Benefits

✅ **For Workers:**
- Get personalized career advice
- Learn in-demand skills
- Identify higher-paying opportunities
- Improve earnings on ShiftSync

✅ **For Platform:**
- Increased worker engagement
- Better skill-job matching
- Reduced churn
- Differentiated experience

✅ **Technical:**
- Powered by Google's Gemini AI
- Scalable architecture
- Real-time responses
- Zero infrastructure cost (API-based)

---

## 📞 Support

For issues or questions:
1. Check this setup guide
2. Review backend logs: `npm start` output
3. Check Android logcat: `adb logcat`
4. Verify API key is valid
5. Ensure MongoDB is running

---

**Last Updated:** March 25, 2026  
**Status:** ✅ Production Ready  
**API Version:** v1.0

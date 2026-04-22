const express = require('express');
const cors = require('cors');
require('dotenv').config();

const connectDB = require('./lib/db');

// Create Express app
const app = express();

// Basic middleware
app.use(cors());
app.use(express.json());

// Simple MVC-style structure
// Routes
const homeRouter = require('./routes/home');
const userRouter = require('./routes/user');
const shiftRouter = require('./routes/shift');
const swapRouter = require('./routes/swap');
const leaveRouter = require('./routes/leave');
const activityRouter = require('./routes/activity');
const notificationRouter = require('./routes/notification');
const chatRouter = require('./routes/chat');
const aiRouter = require('./routes/ai');
const analyticsRouter = require('./routes/analytics');

app.use('/', homeRouter);
app.use('/api/users', userRouter);
app.use('/api/shifts', shiftRouter);
app.use('/api/swaps', swapRouter);
app.use('/api/leaves', leaveRouter);
app.use('/api/activity', activityRouter);
app.use('/api/notifications', notificationRouter);
app.use('/api/chat', chatRouter);
app.use('/api/ai', aiRouter);
app.use('/api/analytics', analyticsRouter);

// Fallback 404
app.use((req, res) => {
  res.status(404).json({ message: 'Not found' });
});

// Error handler
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ message: 'Internal server error' });
});

// Database connection and server start
// HOST/PORT notes:
// - Browser/Postman on this PC:       http://localhost:3000
// - Android emulator (Android Studio): http://10.0.2.2:3000
// - Physical device on same Wi-Fi:    http://<your-lan-ip>:3000
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

connectDB()
  .then(() => {
    const server = app.listen(PORT, HOST, () => {
      console.log('Server addresses:');      
      console.log(`- Localhost (PC):       http://localhost:${PORT}`);
      console.log(`- Android emulator:     http://10.0.2.2:${PORT}`);
      console.log(`- LAN (physical phone): http://<your-lan-ip>:${PORT}`);
    });

    server.on('error', (err) => {
      if (err.code === 'EADDRINUSE') {
        console.error(`Port ${PORT} is already in use.`);
        console.error('Either stop the existing server process or change PORT in .env.');
        process.exit(1);
      }

      console.error('Server startup error', err);
      process.exit(1);
    });
  })
  .catch((err) => {
    console.error('Failed to connect to MongoDB', err);
    process.exit(1);
  });


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
app.use('/', homeRouter);
app.use('/api/users', userRouter);

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
    app.listen(PORT, HOST, () => {
      console.log('Server addresses:');      
      console.log(`- Localhost (PC):       http://localhost:${PORT}`);
      console.log(`- Android emulator:     http://10.0.2.2:${PORT}`);
      console.log(`- LAN (physical phone): http://<your-lan-ip>:${PORT}`);
    });
  })
  .catch((err) => {
    console.error('Failed to connect to MongoDB', err);
    process.exit(1);
  });


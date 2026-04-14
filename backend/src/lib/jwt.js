const jwt = require('jsonwebtoken');
const User = require('../models/User');

const JWT_SECRET = process.env.JWT_SECRET || 'MAD_Project';
const JWT_EXPIRES_IN = '7d';

const generateToken = (user) => {
  return jwt.sign(
    {
      userId: user._id.toString()
    },
    JWT_SECRET,
    { expiresIn: JWT_EXPIRES_IN }
  );
};

const authMiddleware = (req, res, next) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Authorization token missing' });
  }

  const token = authHeader.split(' ')[1];

  try {
    const payload = jwt.verify(token, JWT_SECRET);
    req.user = { id: payload.userId };
    return next();
  } catch (err) {
    console.error('JWT verify error', err);
    return res.status(401).json({ message: 'Invalid or expired token' });
  }
};

const requireRole = (allowedRole) => {
  return async (req, res, next) => {
    try {
      const user = await User.findById(req.user.id).select('role');
      if (!user) {
        return res.status(404).json({ message: 'User not found' });
      }

      if (user.role !== allowedRole) {
        return res.status(403).json({ message: 'Access denied for this role' });
      }

      return next();
    } catch (err) {
      console.error('Role check error', err);
      return res.status(500).json({ message: 'Internal server error' });
    }
  };
};

module.exports = {
  generateToken,
  authMiddleware,
  requireRole
};


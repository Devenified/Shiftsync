const jwt = require('jsonwebtoken');

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

module.exports = {
  generateToken,
  authMiddleware
};


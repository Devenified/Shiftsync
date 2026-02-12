const bcrypt = require('bcryptjs');

const User = require('../models/User');
const { generateToken } = require('../lib/jwt');

// POST /api/users/signup
exports.signup = async (req, res) => {
  try {
    const { fullName, email, phoneNumber, companyName, password, hasProfile } = req.body;

    if (!fullName || !email || !phoneNumber || !companyName || !password) {
      return res.status(400).json({ message: 'Missing required fields' });
    }

    const existing = await User.findOne({ email: email.toLowerCase() });
    if (existing) {
      return res.status(409).json({ message: 'Email already in use' });
    }

    const passwordHash = await bcrypt.hash(password, 10);

    const user = await User.create({
      fullName,
      email: email.toLowerCase(),
      phoneNumber,
      companyName,
      passwordHash,
      hasProfile: !!hasProfile
    });

    const token = generateToken(user);

    return res.status(201).json({
      message: 'User created successfully',
      token,
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        companyName: user.companyName,
        hasProfile: user.hasProfile
      }
    });
  } catch (err) {
    console.error('Signup error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/users/login
exports.login = async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ message: 'Email and password are required' });
    }

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      return res.status(401).json({ message: 'Invalid email or password' });
    }

    const ok = await bcrypt.compare(password, user.passwordHash);
    if (!ok) {
      return res.status(401).json({ message: 'Invalid email or password' });
    }

    const token = generateToken(user);

    return res.json({
      message: 'Login successful',
      token,
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        companyName: user.companyName,
        hasProfile: user.hasProfile
      }
    });
  } catch (err) {
    console.error('Login error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/users/me (protected)
exports.getProfile = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select(
      '_id fullName email phoneNumber companyName hasProfile'
    );

    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    return res.json({
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        companyName: user.companyName,
        hasProfile: user.hasProfile
      }
    });
  } catch (err) {
    console.error('Get profile error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};


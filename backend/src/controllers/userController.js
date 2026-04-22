const bcrypt = require('bcryptjs');

const User = require('../models/User');
const Shift = require('../models/Shift');
const { generateToken } = require('../lib/jwt');

// POST /api/users/signup
exports.signup = async (req, res) => {
  try {
    const { fullName, email, phoneNumber, companyName, password, hasProfile, role } = req.body;

    if (!fullName || !email || !phoneNumber || !password || !role) {
      return res.status(400).json({ message: 'Missing required fields' });
    }

    if (!['worker', 'employer'].includes(role)) {
      return res.status(400).json({ message: 'Role must be worker or employer' });
    }
    if (role === 'employer' && !companyName) {
      return res.status(400).json({ message: 'Company name is required for employer signup' });
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
      companyName: companyName || '',
      role,
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
        role: user.role,
        hasProfile: user.hasProfile
      }
    });
  } catch (err) {
    console.error('Signup error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

const loginBase = async (req, res, expectedRole = null) => {
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

    if (expectedRole && user.role !== expectedRole) {
      return res.status(403).json({
        message: `This endpoint is only for ${expectedRole} login`
      });
    }

    // Update login tracking (fire-and-forget)
    User.findByIdAndUpdate(user._id, {
      $inc: { loginCount: 1 },
      lastLogin: new Date()
    }).catch(() => {});

    const token = generateToken(user);

    return res.json({
      message: 'Login successful',
      token,
      role: user.role,
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        companyName: user.companyName,
        role: user.role,
        hasProfile: user.hasProfile
      }
    });
  } catch (err) {
    console.error('Login error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/users/login
exports.login = async (req, res) => loginBase(req, res);

// POST /api/users/login-worker
exports.loginWorker = async (req, res) => loginBase(req, res, 'worker');

// POST /api/users/login-employer
exports.loginEmployer = async (req, res) => loginBase(req, res, 'employer');

// GET /api/users/me (protected)
exports.getProfile = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select(
      '_id fullName email phoneNumber companyName role hasProfile profilePhoto dateOfBirth gender address location skills skillLevel experienceYears previousWork education bio languages certifications preferredWorkTypes preferredLocations expectedWage wageNegotiable workFlexibility isAvailable availableFrom availableTo preferredWorkHours isVerified verificationDocuments emergencyContact rating totalReviews completedShifts totalEarnings averageResponseTime onTimeCompletionRate notifications isActive lastLogin loginCount'
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
        role: user.role,
        hasProfile: user.hasProfile,
        profilePhoto: user.profilePhoto,
        dateOfBirth: user.dateOfBirth,
        gender: user.gender,
        address: user.address,
        location: user.location,
        skills: user.skills,
        skillLevel: user.skillLevel,
        experienceYears: user.experienceYears,
        previousWork: user.previousWork,
        education: user.education,
        bio: user.bio,
        languages: user.languages,
        certifications: user.certifications,
        preferredWorkTypes: user.preferredWorkTypes,
        preferredLocations: user.preferredLocations,
        expectedWage: user.expectedWage,
        wageNegotiable: user.wageNegotiable,
        workFlexibility: user.workFlexibility,
        isAvailable: user.isAvailable,
        availableFrom: user.availableFrom,
        availableTo: user.availableTo,
        preferredWorkHours: user.preferredWorkHours,
        isVerified: user.isVerified,
        verificationDocuments: user.verificationDocuments,
        emergencyContact: user.emergencyContact,
        rating: user.rating,
        totalReviews: user.totalReviews,
        completedShifts: user.completedShifts,
        totalEarnings: user.totalEarnings,
        averageResponseTime: user.averageResponseTime,
        onTimeCompletionRate: user.onTimeCompletionRate,
        notifications: user.notifications,
        isActive: user.isActive,
        lastLogin: user.lastLogin,
        loginCount: user.loginCount
      }
    });
  } catch (err) {
    console.error('Get profile error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/users/me (protected)
exports.updateProfile = async (req, res) => {
  try {
    const allowedFields = [
      'fullName',
      'phoneNumber',
      'companyName',
      'hasProfile',
      'profilePhoto',
      'dateOfBirth',
      'gender',
      'address',
      'location',
      'skills',
      'skillLevel',
      'experienceYears',
      'previousWork',
      'education',
      'bio',
      'languages',
      'certifications',
      'preferredWorkTypes',
      'preferredLocations',
      'expectedWage',
      'wageNegotiable',
      'workFlexibility',
      'isAvailable',
      'availableFrom',
      'availableTo',
      'preferredWorkHours',
      'emergencyContact',
      'notifications'
    ];

    const updates = {};
    allowedFields.forEach((field) => {
      if (req.body[field] !== undefined) {
        updates[field] = req.body[field];
      }
    });

    // Mark profile as complete if essential fields are provided
    if (updates.skills && updates.skills.length > 0 && 
        updates.experienceYears !== undefined && 
        updates.location) {
      updates.hasProfile = true;
    }

    const user = await User.findByIdAndUpdate(req.user.id, updates, {
      returnDocument: 'after',
      runValidators: true
    }).select(
      '_id fullName email phoneNumber companyName role hasProfile profilePhoto dateOfBirth gender address location skills skillLevel experienceYears previousWork education bio languages certifications preferredWorkTypes preferredLocations expectedWage wageNegotiable workFlexibility isAvailable availableFrom availableTo preferredWorkHours isVerified verificationDocuments emergencyContact rating totalReviews completedShifts totalEarnings averageResponseTime onTimeCompletionRate notifications isActive lastLogin loginCount'
    );

    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    return res.json({
      message: 'Profile updated successfully',
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        companyName: user.companyName,
        role: user.role,
        hasProfile: user.hasProfile,
        profilePhoto: user.profilePhoto,
        dateOfBirth: user.dateOfBirth,
        gender: user.gender,
        address: user.address,
        location: user.location,
        skills: user.skills,
        skillLevel: user.skillLevel,
        experienceYears: user.experienceYears,
        previousWork: user.previousWork,
        education: user.education,
        bio: user.bio,
        languages: user.languages,
        certifications: user.certifications,
        preferredWorkTypes: user.preferredWorkTypes,
        preferredLocations: user.preferredLocations,
        expectedWage: user.expectedWage,
        wageNegotiable: user.wageNegotiable,
        workFlexibility: user.workFlexibility,
        isAvailable: user.isAvailable,
        availableFrom: user.availableFrom,
        availableTo: user.availableTo,
        preferredWorkHours: user.preferredWorkHours,
        isVerified: user.isVerified,
        verificationDocuments: user.verificationDocuments,
        emergencyContact: user.emergencyContact,
        rating: user.rating,
        totalReviews: user.totalReviews,
        completedShifts: user.completedShifts,
        totalEarnings: user.totalEarnings,
        averageResponseTime: user.averageResponseTime,
        onTimeCompletionRate: user.onTimeCompletionRate,
        notifications: user.notifications,
        isActive: user.isActive,
        lastLogin: user.lastLogin,
        loginCount: user.loginCount
      }
    });
  } catch (err) {
    console.error('Update profile error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/users/worker-dashboard (protected, worker only)
exports.getWorkerDashboard = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select(
      '_id fullName role isAvailable rating completedShifts totalEarnings'
    );

    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);

    const startOfMonth = new Date();
    startOfMonth.setDate(1);
    startOfMonth.setHours(0, 0, 0, 0);

    const completedShiftsList = await Shift.find({
      assignedWorker: req.user.id,
      status: 'completed'
    });

    let todayEarnings = 0;
    let monthlyEarnings = 0;

    completedShiftsList.forEach(shift => {
      // Use updatedAt to determine completion time
      if (shift.updatedAt && shift.updatedAt >= startOfToday) {
        todayEarnings += shift.wage || 0;
      }
      if (shift.updatedAt && shift.updatedAt >= startOfMonth) {
        monthlyEarnings += shift.wage || 0;
      }
    });

    return res.json({
      message: 'Worker dashboard data fetched successfully',
      dashboard: 'worker',
      summary: {
        userId: user._id,
        fullName: user.fullName,
        role: user.role,
        isAvailable: user.isAvailable,
        rating: user.rating,
        completedShifts: completedShiftsList.length > 0 ? completedShiftsList.length : user.completedShifts,
        totalEarnings: user.totalEarnings,
        todayEarnings,
        monthlyEarnings
      }
    });
  } catch (err) {
    console.error('Worker dashboard error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/users/employer-dashboard (protected, employer only)
exports.getEmployerDashboard = async (req, res) => {
  try {
    const employer = await User.findById(req.user.id).select('_id fullName role');
    if (!employer) {
      return res.status(404).json({ message: 'User not found' });
    }

    const shifts = await Shift.find({ employer: req.user.id }).select(
      'status applications assignedWorker'
    );

    const totalShifts = shifts.length;
    const openShifts = shifts.filter((shift) => shift.status === 'open').length;
    const assignedShifts = shifts.filter((shift) => shift.status === 'assigned').length;
    const completedShifts = shifts.filter((shift) => shift.status === 'completed').length;
    const totalApplications = shifts.reduce((sum, shift) => sum + shift.applications.length, 0);

    const uniqueWorkerIds = new Set();
    shifts.forEach((shift) => {
      if (shift.assignedWorker) {
        uniqueWorkerIds.add(shift.assignedWorker.toString());
      }
      shift.applications.forEach((application) =>
        uniqueWorkerIds.add(application.worker.toString())
      );
    });

    const upcomingShift = await Shift.findOne({
      employer: req.user.id,
      status: { $in: ['open', 'assigned'] }
    }).sort({ shiftDate: 1, startTime: 1 });

    return res.json({
      message: 'Employer dashboard data fetched successfully',
      dashboard: 'employer',
      summary: {
        userId: employer._id,
        fullName: employer.fullName,
        role: employer.role,
        activeShifts: openShifts + assignedShifts,
        totalShifts,
        openShifts,
        assignedShifts,
        completedShifts,
        totalApplications,
        totalWorkersEngaged: uniqueWorkerIds.size,
        nextShift: upcomingShift
          ? {
              id: upcomingShift._id,
              title: upcomingShift.title,
              shiftDate: upcomingShift.shiftDate,
              startTime: upcomingShift.startTime,
              endTime: upcomingShift.endTime
            }
          : null
      }
    });
  } catch (err) {
    console.error('Employer dashboard error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/users/logout (protected)
exports.logout = async (req, res) => {
  try {
    // In a JWT-based system, the token is stateless on the server
    // The client simply needs to discard the token
    // We can add additional cleanup logic here if needed
    
    return res.json({
      message: 'Logout successful'
    });
  } catch (err) {
    console.error('Logout error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/users/workers/search (protected, employer only)
exports.searchWorkers = async (req, res) => {
  try {
    const { skill, location, minRating, availableOnly } = req.query;
    const filter = { role: 'worker' };

    if (skill) {
      filter.skills = { $elemMatch: { $regex: skill, $options: 'i' } };
    }
    if (location) {
      filter.location = { $regex: location, $options: 'i' };
    }
    if (minRating) {
      filter.rating = { $gte: Number(minRating) || 0 };
    }
    if (availableOnly === 'true') {
      filter.isAvailable = true;
    }

    const workers = await User.find(filter)
      .select(
        '_id fullName phoneNumber skills experienceYears location isAvailable rating completedShifts totalEarnings'
      )
      .sort({ rating: -1, completedShifts: -1, createdAt: -1 });

    return res.json({
      workers: workers.map((worker) => ({
        id: worker._id,
        fullName: worker.fullName,
        phoneNumber: worker.phoneNumber,
        skills: worker.skills,
        experienceYears: worker.experienceYears,
        location: worker.location,
        isAvailable: worker.isAvailable,
        rating: worker.rating,
        completedShifts: worker.completedShifts,
        totalEarnings: worker.totalEarnings
      }))
    });
  } catch (err) {
    console.error('Worker search error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/users/:id (protected, employer only)
// Allows employers to view a worker profile for hiring decisions.
exports.getUserByIdForEmployer = async (req, res) => {
  try {
    const user = await User.findById(req.params.id).select(
      '_id fullName email phoneNumber role profilePhoto location skills skillLevel experienceYears previousWork education bio languages certifications preferredWorkTypes preferredLocations expectedWage wageNegotiable workFlexibility isAvailable preferredWorkHours isVerified rating totalReviews completedShifts totalEarnings averageResponseTime onTimeCompletionRate lastLogin'
    );

    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }
    if (user.role !== 'worker') {
      return res.status(403).json({ message: 'Only worker profiles are viewable' });
    }

    return res.json({
      user: {
        id: user._id,
        fullName: user.fullName,
        email: user.email,
        phoneNumber: user.phoneNumber,
        role: user.role,
        profilePhoto: user.profilePhoto,
        location: user.location,
        skills: user.skills,
        skillLevel: user.skillLevel,
        experienceYears: user.experienceYears,
        previousWork: user.previousWork,
        education: user.education,
        bio: user.bio,
        languages: user.languages,
        certifications: user.certifications,
        preferredWorkTypes: user.preferredWorkTypes,
        preferredLocations: user.preferredLocations,
        expectedWage: user.expectedWage,
        wageNegotiable: user.wageNegotiable,
        workFlexibility: user.workFlexibility,
        isAvailable: user.isAvailable,
        preferredWorkHours: user.preferredWorkHours,
        isVerified: user.isVerified,
        rating: user.rating,
        totalReviews: user.totalReviews,
        completedShifts: user.completedShifts,
        totalEarnings: user.totalEarnings,
        averageResponseTime: user.averageResponseTime,
        onTimeCompletionRate: user.onTimeCompletionRate,
        lastLogin: user.lastLogin
      }
    });
  } catch (err) {
    console.error('Get user by id (employer) error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};


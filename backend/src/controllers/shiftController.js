const Shift = require('../models/Shift');
const User = require('../models/User');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');

// POST /api/shifts (employer)
exports.createShift = async (req, res) => {
  try {
    const { title, description, skillRequired, location, shiftDate, startTime, endTime, wage } =
      req.body;

    if (!title || !skillRequired || !location || !shiftDate || !startTime || !endTime || wage == null) {
      return res.status(400).json({ message: 'Missing required fields' });
    }

    const shift = await Shift.create({
      employer: req.user.id,
      title,
      description,
      skillRequired,
      location,
      shiftDate,
      startTime,
      endTime,
      wage
    });

    // Activity Feed
    await ActivityFeed.create({
      user: req.user.id,
      action: `posted a new shift: ${title}`,
      type: 'shift_assignment'
    });

    return res.status(201).json({
      message: 'Shift created successfully',
      shift
    });
  } catch (err) {
    console.error('Create shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/shifts/open (worker)
exports.listOpenShifts = async (req, res) => {
  try {
    const { skill, location } = req.query;
    const filter = { status: 'open' };

    if (skill) {
      filter.skillRequired = { $regex: skill, $options: 'i' };
    }
    if (location) {
      filter.location = { $regex: location, $options: 'i' };
    }

    const shifts = await Shift.find(filter)
      .populate('employer', 'fullName companyName')
      .sort({ createdAt: -1 });

    const shiftsWithApplyStatus = shifts.map((shift) => {
      const alreadyApplied = shift.applications.some(
        (app) => app.worker.toString() === req.user.id
      );
      return {
        ...shift.toObject(),
        hasApplied: alreadyApplied
      };
    });

    return res.json({ shifts: shiftsWithApplyStatus });
  } catch (err) {
    console.error('List open shifts error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/shifts/:id/apply (worker)
exports.applyToShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }

    if (shift.status !== 'open') {
      return res.status(400).json({ message: 'Shift is not open for applications' });
    }

    const alreadyApplied = shift.applications.some(
      (app) => app.worker.toString() === req.user.id
    );
    if (alreadyApplied) {
      return res.status(409).json({ message: 'Already applied to this shift' });
    }

    shift.applications.push({ worker: req.user.id, status: 'pending' });
    await shift.save();

    return res.json({ message: 'Applied to shift successfully' });
  } catch (err) {
    console.error('Apply shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/shifts/employer/my (employer)
exports.listEmployerShifts = async (req, res) => {
  try {
    const shifts = await Shift.find({ employer: req.user.id })
      .populate('assignedWorker', 'fullName email phoneNumber')
      .populate('applications.worker', 'fullName email phoneNumber')
      .sort({ createdAt: -1 });

    return res.json({ shifts });
  } catch (err) {
    console.error('List employer shifts error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/shifts/:id/applications/:workerId (employer)
exports.reviewApplication = async (req, res) => {
  try {
    const { decision } = req.body;
    if (!['accepted', 'rejected'].includes(decision)) {
      return res.status(400).json({ message: 'Decision must be accepted or rejected' });
    }

    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }

    if (shift.employer.toString() !== req.user.id) {
      return res.status(403).json({ message: 'You can only review your own shifts' });
    }

    const app = shift.applications.find(
      (item) => item.worker.toString() === req.params.workerId
    );
    if (!app) {
      return res.status(404).json({ message: 'Application not found' });
    }

    app.status = decision;

    if (decision === 'accepted') {
      shift.assignedWorker = req.params.workerId;
      shift.status = 'assigned';

      // Mark all other pending applications as rejected.
      shift.applications.forEach((item) => {
        if (item.worker.toString() !== req.params.workerId && item.status === 'pending') {
          item.status = 'rejected';
        }
      });
    }

    await shift.save();

    // Activity Feed
    await ActivityFeed.create({
      user: req.user.id,
      action: `${decision} an application for ${shift.title}`,
      type: 'shift_assignment',
      relatedId: shift._id
    });

    // Notify Worker
    await Notification.create({
      userId: req.params.workerId,
      message: `Your application for ${shift.title} was ${decision}.`,
      type: 'shift_change',
      relatedId: shift._id
    });

    return res.json({ message: `Application ${decision}` });
  } catch (err) {
    console.error('Review application error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/shifts/:id/complete (employer)
exports.completeShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }

    if (shift.employer.toString() !== req.user.id) {
      return res.status(403).json({ message: 'You can only complete your own shifts' });
    }

    if (!shift.assignedWorker) {
      return res.status(400).json({ message: 'Cannot complete shift without assigned worker' });
    }

    if (shift.status === 'completed') {
      return res.status(400).json({ message: 'Shift already completed' });
    }

    shift.status = 'completed';
    await shift.save();

    // Activity Feed
    await ActivityFeed.create({
      user: shift.assignedWorker,
      action: `completed shift: ${shift.title}`,
      type: 'attendance',
      relatedId: shift._id
    });

    await User.findByIdAndUpdate(shift.assignedWorker, {
      $inc: {
        completedShifts: 1,
        totalEarnings: shift.wage
      }
    });

    return res.json({ message: 'Shift marked as completed' });
  } catch (err) {
    console.error('Complete shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/shifts/worker/my (worker)
exports.listWorkerShifts = async (req, res) => {
  try {
    const shifts = await Shift.find({
      $or: [{ assignedWorker: req.user.id }, { 'applications.worker': req.user.id }]
    })
      .populate('employer', 'fullName companyName phoneNumber')
      .sort({ createdAt: -1 });

    const shiftsWithApplicationStatus = shifts.map((shift) => {
      const workerApplication = shift.applications.find(
        (app) => app.worker.toString() === req.user.id
      );

      return {
        ...shift.toObject(),
        applicationStatus: workerApplication ? workerApplication.status : null
      };
    });

    return res.json({ shifts: shiftsWithApplicationStatus });
  } catch (err) {
    console.error('List worker shifts error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};


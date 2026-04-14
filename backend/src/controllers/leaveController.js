const LeaveRequest = require('../models/LeaveRequest');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');

// POST /api/leaves/request
exports.requestLeave = async (req, res) => {
  try {
    const { leaveType, startDate, endDate, reason } = req.body;
    if (!leaveType || !startDate || !endDate) {
      return res.status(400).json({ message: 'Missing required fields' });
    }

    const leave = await LeaveRequest.create({
      userId: req.user.id,
      leaveType,
      startDate,
      endDate,
      reason,
      status: 'pending'
    });

    // Activity Feed
    await ActivityFeed.create({
      user: req.user.id,
      action: `requested leave from ${startDate} to ${endDate}`,
      type: 'leave_request',
      relatedId: leave._id
    });

    // Notify Managers (In a real app, find users with role 'employer')
    // For now, we'll just log it. Realistically we'd notify specific managers.

    return res.status(201).json({ message: 'Leave request submitted', leave });
  } catch (err) {
    console.error('Leave request error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/leaves/my
exports.getMyLeaves = async (req, res) => {
  try {
    const leaves = await LeaveRequest.find({ userId: req.user.id }).sort({ createdAt: -1 });
    return res.json({ leaves });
  } catch (err) {
    console.error('Get my leaves error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/leaves/all (Employer only)
exports.getAllLeaves = async (req, res) => {
  try {
    const leaves = await LeaveRequest.find().populate('userId', 'fullName email').sort({ createdAt: -1 });
    return res.json({ leaves });
  } catch (err) {
    console.error('Get all leaves error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/leaves/:id/approve (Employer only)
exports.approveLeave = async (req, res) => {
  try {
    const { decision } = req.body; // 'approved' or 'rejected'
    const leave = await LeaveRequest.findById(req.params.id);
    if (!leave) return res.status(404).json({ message: 'Leave not found' });

    leave.status = decision;
    await leave.save();

    // Notify User
    await Notification.create({
      userId: leave.userId,
      message: `Your leave request for ${leave.startDate} has been ${decision}.`,
      type: 'leave_approval',
      relatedId: leave._id
    });

    return res.json({ message: `Leave ${decision}`, leave });
  } catch (err) {
    console.error('Approve leave error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

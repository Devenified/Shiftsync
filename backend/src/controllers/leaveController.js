const LeaveRequest = require('../models/LeaveRequest');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');
const User = require('../models/User');

// GET /api/leaves/balance
exports.getLeaveBalance = async (req, res) => {
  try {
    const currentYear = new Date().getFullYear();
    const start = new Date(currentYear, 0, 1);
    const end = new Date(currentYear, 11, 31, 23, 59, 59, 999);
    const approvedLeaves = await LeaveRequest.find({
      userId: req.user.id,
      status: 'approved',
      startDate: { $gte: start, $lte: end }
    }).select('leaveType startDate endDate');

    const totals = { sick: 7, casual: 10, paid: 15 };
    const used = { sick: 0, casual: 0, paid: 0 };

    approvedLeaves.forEach((leave) => {
      const startDate = new Date(leave.startDate);
      const endDate = new Date(leave.endDate);
      const diffDays = Math.max(
        1,
        Math.ceil((endDate.getTime() - startDate.getTime()) / (24 * 60 * 60 * 1000)) + 1
      );
      if (used[leave.leaveType] !== undefined) {
        used[leave.leaveType] += diffDays;
      }
    });

    return res.json({
      balance: {
        sick: Math.max(0, totals.sick - used.sick),
        casual: Math.max(0, totals.casual - used.casual),
        paid: Math.max(0, totals.paid - used.paid)
      }
    });
  } catch (err) {
    console.error('Leave balance error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

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

    const requester = await User.findById(req.user.id).select('fullName');
    const employers = await User.find({ role: 'employer', isActive: true }).select('_id');
    if (employers.length > 0) {
      await Notification.insertMany(
        employers.map((employer) => ({
          userId: employer._id,
          message: `${requester ? requester.fullName : 'A user'} requested ${leaveType} leave.`,
          type: 'leave_request',
          relatedId: leave._id
        }))
      );
    }

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
    if (!['approved', 'rejected'].includes(decision)) {
      return res.status(400).json({ message: 'Decision must be approved or rejected' });
    }
    const leave = await LeaveRequest.findById(req.params.id);
    if (!leave) return res.status(404).json({ message: 'Leave not found' });

    leave.status = decision;
    await leave.save();

    // Notify User
    await Notification.create({
      userId: leave.userId,
      message: `Your leave request for ${leave.startDate} has been ${decision}.`,
      type: decision === 'approved' ? 'leave_approved' : 'leave_rejected',
      relatedId: leave._id
    });

    await ActivityFeed.create({
      user: leave.userId,
      action: `leave request was ${decision}`,
      type: 'leave_request',
      relatedId: leave._id
    });

    return res.json({ message: `Leave ${decision}`, leave });
  } catch (err) {
    console.error('Approve leave error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

const SwapRequest = require('../models/SwapRequest');
const Shift = require('../models/Shift');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');
const User = require('../models/User');

// GET /api/swaps/options
exports.getSwapOptions = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select('role fullName');
    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    let shifts = [];
    let teamMembers = [];

    if (user.role === 'worker') {
      shifts = await Shift.find({
        assignedWorker: req.user.id,
        status: { $in: ['assigned', 'open'] }
      })
        .sort({ shiftDate: 1, startTime: 1 })
        .limit(10);

      teamMembers = await User.find({
        role: 'worker',
        _id: { $ne: req.user.id },
        isActive: true
      })
        .select('_id fullName skills preferredWorkHours isAvailable')
        .sort({ isAvailable: -1, rating: -1, completedShifts: -1 })
        .limit(25);
    } else {
      shifts = await Shift.find({
        employer: req.user.id,
        status: { $in: ['open', 'assigned'] }
      })
        .sort({ shiftDate: 1, startTime: 1 })
        .limit(10);

      teamMembers = await User.find({
        role: 'worker',
        isActive: true
      })
        .select('_id fullName skills preferredWorkHours isAvailable')
        .sort({ isAvailable: -1, rating: -1, completedShifts: -1 })
        .limit(25);
    }

    return res.json({
      shifts: shifts.map((shift) => ({
        id: shift._id,
        title: shift.title,
        shiftDate: shift.shiftDate,
        startTime: shift.startTime,
        endTime: shift.endTime,
        location: shift.location,
        status: shift.status
      })),
      teamMembers: teamMembers.map((member) => ({
        id: member._id,
        name: member.fullName,
        role: (member.skills || []).slice(0, 2).join(', ') || member.preferredWorkHours || 'Worker',
        availability: member.isAvailable ? 'Available now' : 'Availability not set'
      }))
    });
  } catch (err) {
    console.error('Swap options error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// POST /api/swaps/request
exports.requestSwap = async (req, res) => {
  try {
    const { targetUserId, shiftId, notes, preferredDate } = req.body;
    if (!targetUserId || !shiftId) {
      return res.status(400).json({ message: 'Missing target user or shift ID' });
    }

    const requester = await User.findById(req.user.id).select('fullName role');
    const targetUser = await User.findById(targetUserId).select('fullName role');
    if (!requester || !targetUser) {
      return res.status(404).json({ message: 'User not found' });
    }
    if (targetUser.role !== 'worker') {
      return res.status(400).json({ message: 'Swap target must be a worker' });
    }
    if (String(targetUser._id) === String(req.user.id)) {
      return res.status(400).json({ message: 'You cannot request a swap with yourself' });
    }

    const shift = await Shift.findById(shiftId);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }
    if (requester.role === 'worker' && String(shift.assignedWorker) !== String(req.user.id)) {
      return res.status(403).json({ message: 'You can only request swaps for your own assigned shifts' });
    }
    if (requester.role === 'employer' && String(shift.employer) !== String(req.user.id)) {
      return res.status(403).json({ message: 'You can only manage swaps for your own shifts' });
    }

    const existing = await SwapRequest.findOne({
      requester: req.user.id,
      targetUser: targetUserId,
      shiftId,
      status: 'pending'
    });
    if (existing) {
      return res.status(409).json({ message: 'A pending swap request already exists' });
    }

    const swapRequest = await SwapRequest.create({
      requester: req.user.id,
      targetUser: targetUserId,
      shiftId,
      notes: notes || preferredDate || '',
      status: 'pending'
    });

    // Log Activity
    await ActivityFeed.create({
      user: req.user.id,
      action: `requested a shift swap for shift on ${shift.shiftDate}`,
      type: 'shift_swap',
      relatedId: swapRequest._id
    });

    // Notify target user
    await Notification.create({
      userId: targetUserId,
      message: `${requester.fullName} requested a shift swap with you.`,
      type: 'swap_request',
      relatedId: swapRequest._id
    });

    return res.status(201).json({ message: 'Swap request submitted', swapRequest });
  } catch (err) {
    console.error('Swap request error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/swaps/received
exports.getReceivedSwaps = async (req, res) => {
  try {
    const swaps = await SwapRequest.find({ targetUser: req.user.id })
      .populate('requester', 'fullName email')
      .populate('shiftId')
      .sort({ createdAt: -1 });
    return res.json({ swaps });
  } catch (err) {
    console.error('Get received swaps error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/swaps/:id/respond
exports.respondToSwap = async (req, res) => {
  try {
    const { decision } = req.body; // 'approved' or 'rejected'
    if (!['approved', 'rejected'].includes(decision)) {
      return res.status(400).json({ message: 'Invalid decision' });
    }

    const swap = await SwapRequest.findById(req.params.id);
    if (!swap) {
      return res.status(404).json({ message: 'Swap request not found' });
    }

    if (swap.targetUser.toString() !== req.user.id) {
      return res.status(403).json({ message: 'Unauthorized' });
    }

    const shift = await Shift.findById(swap.shiftId);
    if (!shift) {
      return res.status(404).json({ message: 'Associated shift not found' });
    }

    swap.status = decision;
    await swap.save();

    if (decision === 'approved') {
      shift.assignedWorker = swap.targetUser;
      shift.status = 'assigned';

      let requesterApplication = shift.applications.find(
        (item) => item.worker.toString() === swap.requester.toString()
      );
      let targetApplication = shift.applications.find(
        (item) => item.worker.toString() === swap.targetUser.toString()
      );

      if (requesterApplication) requesterApplication.status = 'rejected';
      if (targetApplication) {
        targetApplication.status = 'accepted';
      } else {
        shift.applications.push({ worker: swap.targetUser, status: 'accepted' });
      }
      await shift.save();
    }

    // Notify requester
    await Notification.create({
      userId: swap.requester,
      message: `${req.user.fullName} ${decision} your swap request.`,
      type: decision === 'approved' ? 'swap_approved' : 'swap_rejected',
      relatedId: swap._id
    });

    await ActivityFeed.create({
      user: req.user.id,
      action: `${decision} a shift swap request from requester.`,
      type: 'shift_swap',
      relatedId: swap._id
    });

    return res.json({ message: `Swap request ${decision}`, swap });
  } catch (err) {
    console.error('Respond to swap error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

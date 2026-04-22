const SwapRequest = require('../models/SwapRequest');
const Shift = require('../models/Shift');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');

// POST /api/swaps/request
exports.requestSwap = async (req, res) => {
  try {
    const { targetUserId, shiftId, notes } = req.body;
    if (!targetUserId || !shiftId) {
      return res.status(400).json({ message: 'Missing target user or shift ID' });
    }

    const shift = await Shift.findById(shiftId);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }

    const swapRequest = await SwapRequest.create({
      requester: req.user.id,
      targetUser: targetUserId,
      shiftId,
      notes,
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
      message: `${req.user.fullName} requested a shift swap with you.`,
      type: 'shift_change',
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

    swap.status = decision;
    await swap.save();

    if (decision === 'approved') {
      // Logic for manager approval might be needed here too
      // For simplicity, we'll auto-update the shift if approved by target user
      // and maybe log a notification for manager later
      
      // Update the shift's assigned worker if manager doesn't need to approve
      // But typically manager approves too. Let's assume manager approval is needed.
      // So status 'approved' here means 'target user approved'.
      // Final status 'completed_swap' might be better.
    }

    // Notify requester
    await Notification.create({
      userId: swap.requester,
      message: `${req.user.fullName} ${decision} your swap request.`,
      type: 'shift_change',
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

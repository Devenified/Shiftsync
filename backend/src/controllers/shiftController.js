const Shift = require('../models/Shift');
const User = require('../models/User');
const ActivityFeed = require('../models/ActivityFeed');
const Notification = require('../models/Notification');

function acceptedCount(shift) {
  return shift.applications.filter((a) => a.status === 'accepted').length;
}

function slotsRemaining(shift) {
  const needed = Math.max(1, shift.workersNeeded || 1);
  return Math.max(0, needed - acceptedCount(shift));
}

// POST /api/shifts (employer)
exports.createShift = async (req, res) => {
  try {
    const {
      title,
      description,
      skillRequired,
      location,
      shiftDate,
      startTime,
      endTime,
      wage,
      workersNeeded
    } = req.body;

    if (!title || !skillRequired || !location || !shiftDate || !startTime || !endTime || wage == null) {
      return res.status(400).json({ message: 'Missing required fields' });
    }

    const needed = Math.max(1, Number(workersNeeded) || 1);

    const shift = await Shift.create({
      employer: req.user.id,
      title,
      description,
      skillRequired,
      location,
      shiftDate,
      startTime,
      endTime,
      wage,
      workersNeeded: needed
    });

    await ActivityFeed.create({
      user: req.user.id,
      action: `posted a new shift: ${title} (need ${needed})`,
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
      const filled = acceptedCount(shift);
      const needed = Math.max(1, shift.workersNeeded || 1);
      return {
        ...shift.toObject(),
        hasApplied: alreadyApplied,
        slotsFilled: filled,
        slotsRemaining: Math.max(0, needed - filled)
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

    if (slotsRemaining(shift) <= 0) {
      return res.status(400).json({ message: 'Shift is already fully staffed' });
    }

    const alreadyApplied = shift.applications.some(
      (app) => app.worker.toString() === req.user.id
    );
    if (alreadyApplied) {
      return res.status(409).json({ message: 'Already applied to this shift' });
    }

    shift.applications.push({ worker: req.user.id, status: 'pending' });
    await shift.save();

    await ActivityFeed.create({
      user: req.user.id,
      action: `applied for shift: ${shift.title}`,
      type: 'shift_assignment',
      relatedId: shift._id
    });

    await Notification.create({
      userId: shift.employer,
      message: `A new worker applied for "${shift.title}".`,
      type: 'shift_assignment',
      relatedId: shift._id
    });

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
      .populate('assignedWorker', 'fullName email phoneNumber rating completedShifts skills')
      .populate('assignedWorkers', 'fullName email phoneNumber rating completedShifts skills')
      .populate(
        'applications.worker',
        'fullName email phoneNumber rating completedShifts skills experienceYears location isAvailable'
      )
      .sort({ createdAt: -1 });

    const enriched = shifts.map((s) => {
      const filled = acceptedCount(s);
      const needed = Math.max(1, s.workersNeeded || 1);
      return {
        ...s.toObject(),
        slotsFilled: filled,
        slotsRemaining: Math.max(0, needed - filled)
      };
    });

    return res.json({ shifts: enriched });
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

    if (shift.status === 'completed' || shift.status === 'cancelled') {
      return res.status(400).json({ message: 'Shift is no longer open for review' });
    }

    const app = shift.applications.find(
      (item) => item.worker.toString() === req.params.workerId
    );
    if (!app) {
      return res.status(404).json({ message: 'Application not found' });
    }

    if (decision === 'accepted') {
      if (app.status === 'accepted') {
        return res.status(409).json({ message: 'Worker already accepted' });
      }
      if (slotsRemaining(shift) <= 0) {
        return res.status(400).json({ message: 'No slots remaining for this shift' });
      }

      app.status = 'accepted';

      const workerObjectId = new (require('mongoose').Types.ObjectId)(req.params.workerId);
      const alreadyInList = (shift.assignedWorkers || []).some(
        (id) => id.toString() === req.params.workerId
      );
      if (!alreadyInList) {
        shift.assignedWorkers.push(workerObjectId);
      }
      if (!shift.assignedWorker) {
        shift.assignedWorker = workerObjectId;
      }

      // If all slots are filled, lock the shift and auto-reject remaining pending applicants.
      if (slotsRemaining(shift) <= 0) {
        shift.status = 'assigned';
        shift.applications.forEach((item) => {
          if (item.status === 'pending') {
            item.status = 'rejected';
          }
        });
      }
    } else {
      // Rejection
      if (app.status === 'accepted') {
        // Remove from assignedWorkers if previously accepted.
        shift.assignedWorkers = shift.assignedWorkers.filter(
          (id) => id.toString() !== req.params.workerId
        );
        if (
          shift.assignedWorker &&
          shift.assignedWorker.toString() === req.params.workerId
        ) {
          shift.assignedWorker = shift.assignedWorkers[0] || null;
        }
        // Shift returns to open if we now have capacity.
        if (shift.status === 'assigned' && slotsRemaining(shift) > 0) {
          shift.status = 'open';
        }
      }
      app.status = 'rejected';
    }

    await shift.save();

    await ActivityFeed.create({
      user: req.user.id,
      action: `${decision} an application for ${shift.title}`,
      type: 'shift_assignment',
      relatedId: shift._id
    });

    await Notification.create({
      userId: req.params.workerId,
      message: `Your application for ${shift.title} was ${decision}.`,
      type: decision === 'accepted' ? 'shift_assignment' : 'shift_updated',
      relatedId: shift._id
    });

    return res.json({
      message: `Application ${decision}`,
      slotsFilled: acceptedCount(shift),
      slotsRemaining: slotsRemaining(shift),
      status: shift.status
    });
  } catch (err) {
    console.error('Review application error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/shifts/:id/complete (employer)
// Completes the whole shift and pays every accepted worker.
exports.completeShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }

    if (shift.employer.toString() !== req.user.id) {
      return res.status(403).json({ message: 'You can only complete your own shifts' });
    }

    if (shift.status === 'completed') {
      return res.status(400).json({ message: 'Shift already completed' });
    }

    // Gather all accepted workers (support both legacy single and new multi fields).
    const workerIds = new Set();
    if (shift.assignedWorker) workerIds.add(shift.assignedWorker.toString());
    (shift.assignedWorkers || []).forEach((id) => workerIds.add(id.toString()));
    shift.applications
      .filter((a) => a.status === 'accepted')
      .forEach((a) => workerIds.add(a.worker.toString()));

    if (workerIds.size === 0) {
      return res.status(400).json({
        message: 'Cannot complete a shift with no accepted workers'
      });
    }

    shift.status = 'completed';
    // Make sure assignedWorkers is complete so later queries work.
    shift.assignedWorkers = Array.from(workerIds);
    if (!shift.assignedWorker) {
      shift.assignedWorker = shift.assignedWorkers[0];
    }
    await shift.save();

    const ops = Array.from(workerIds).map((workerId) =>
      User.findByIdAndUpdate(workerId, {
        $inc: {
          completedShifts: 1,
          totalEarnings: shift.wage
        }
      })
    );
    await Promise.all(ops);

    await Promise.all(
      Array.from(workerIds).map((workerId) =>
        Notification.create({
          userId: workerId,
          message: `Your shift "${shift.title}" was marked as completed. \u20B9${shift.wage} credited.`,
          type: 'shift_updated',
          relatedId: shift._id
        })
      )
    );

    await Promise.all(
      Array.from(workerIds).map((workerId) =>
        ActivityFeed.create({
          user: workerId,
          action: `completed shift: ${shift.title}`,
          type: 'attendance',
          relatedId: shift._id
        })
      )
    );

    return res.json({
      message: 'Shift marked as completed',
      paidWorkers: workerIds.size,
      wagePerWorker: shift.wage
    });
  } catch (err) {
    console.error('Complete shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/shifts/:id  (employer who owns it, or worker who applied/assigned)
exports.getShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id)
      .populate('employer', 'fullName companyName phoneNumber email')
      .populate('assignedWorker', 'fullName phoneNumber rating')
      .populate('assignedWorkers', 'fullName phoneNumber rating')
      .populate('applications.worker', 'fullName phoneNumber rating skills');
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }
    const filled = acceptedCount(shift);
    const needed = Math.max(1, shift.workersNeeded || 1);
    return res.json({
      shift: {
        ...shift.toObject(),
        slotsFilled: filled,
        slotsRemaining: Math.max(0, needed - filled)
      }
    });
  } catch (err) {
    console.error('Get shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// PATCH /api/shifts/:id (employer)
exports.updateShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }
    if (shift.employer.toString() !== req.user.id) {
      return res.status(403).json({ message: 'You can only edit your own shifts' });
    }
    if (shift.status !== 'open') {
      return res.status(400).json({ message: 'Only open shifts can be edited' });
    }

    const allowed = [
      'title',
      'description',
      'skillRequired',
      'location',
      'shiftDate',
      'startTime',
      'endTime',
      'wage',
      'workersNeeded'
    ];
    allowed.forEach((f) => {
      if (req.body[f] !== undefined) {
        if (f === 'workersNeeded') {
          const requested = Math.max(1, Number(req.body[f]) || 1);
          if (requested < acceptedCount(shift)) {
            // Silently clamp to at least already-accepted workers so we can't lose staff.
            shift.workersNeeded = acceptedCount(shift);
          } else {
            shift.workersNeeded = requested;
          }
        } else {
          shift[f] = req.body[f];
        }
      }
    });
    await shift.save();

    return res.json({ message: 'Shift updated', shift });
  } catch (err) {
    console.error('Update shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// DELETE /api/shifts/:id (employer) - cancels if open, else 400
exports.cancelShift = async (req, res) => {
  try {
    const shift = await Shift.findById(req.params.id);
    if (!shift) {
      return res.status(404).json({ message: 'Shift not found' });
    }
    if (shift.employer.toString() !== req.user.id) {
      return res.status(403).json({ message: 'You can only cancel your own shifts' });
    }
    if (shift.status === 'completed') {
      return res.status(400).json({ message: 'Cannot cancel a completed shift' });
    }

    shift.status = 'cancelled';
    await shift.save();

    const targetIds = new Set();
    if (shift.assignedWorker) {
      targetIds.add(shift.assignedWorker.toString());
    }
    (shift.assignedWorkers || []).forEach((id) => targetIds.add(id.toString()));
    shift.applications.forEach((a) => targetIds.add(a.worker.toString()));

    if (targetIds.size > 0) {
      await Notification.insertMany(
        Array.from(targetIds).map((id) => ({
          userId: id,
          message: `Shift "${shift.title}" was cancelled by the employer`,
          type: 'shift_cancelled',
          relatedId: shift._id
        }))
      );
    }

    await ActivityFeed.create({
      user: req.user.id,
      action: `cancelled shift: ${shift.title}`,
      type: 'shift_assignment',
      relatedId: shift._id
    });

    return res.json({ message: 'Shift cancelled', shift });
  } catch (err) {
    console.error('Cancel shift error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/shifts/worker/my (worker)
exports.listWorkerShifts = async (req, res) => {
  try {
    const shifts = await Shift.find({
      $or: [
        { assignedWorker: req.user.id },
        { assignedWorkers: req.user.id },
        { 'applications.worker': req.user.id }
      ]
    })
      .populate('employer', 'fullName companyName phoneNumber')
      .sort({ createdAt: -1 });

    const shiftsWithApplicationStatus = shifts.map((shift) => {
      const workerApplication = shift.applications.find(
        (app) => app.worker.toString() === req.user.id
      );
      const filled = acceptedCount(shift);
      const needed = Math.max(1, shift.workersNeeded || 1);
      return {
        ...shift.toObject(),
        applicationStatus: workerApplication ? workerApplication.status : null,
        slotsFilled: filled,
        slotsRemaining: Math.max(0, needed - filled)
      };
    });

    return res.json({ shifts: shiftsWithApplicationStatus });
  } catch (err) {
    console.error('List worker shifts error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

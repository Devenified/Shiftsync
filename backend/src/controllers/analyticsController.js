const mongoose = require('mongoose');

const Shift = require('../models/Shift');
const User = require('../models/User');
const LeaveRequest = require('../models/LeaveRequest');
const SwapRequest = require('../models/SwapRequest');

// GET /api/analytics/employer
exports.employerAnalytics = async (req, res) => {
  try {
    const employerId = new mongoose.Types.ObjectId(req.user.id);

    const [shiftsByStatus, shiftsLast7d, topSkills, topWorkers, leaveStats, swapStats] =
      await Promise.all([
        Shift.aggregate([
          { $match: { employer: employerId } },
          { $group: { _id: '$status', count: { $sum: 1 }, totalWage: { $sum: '$wage' } } }
        ]),
        Shift.aggregate([
          {
            $match: {
              employer: employerId,
              createdAt: { $gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
            }
          },
          {
            $group: {
              _id: {
                $dateToString: { format: '%Y-%m-%d', date: '$createdAt' }
              },
              count: { $sum: 1 }
            }
          },
          { $sort: { _id: 1 } }
        ]),
        Shift.aggregate([
          { $match: { employer: employerId } },
          { $group: { _id: '$skillRequired', count: { $sum: 1 } } },
          { $sort: { count: -1 } },
          { $limit: 5 }
        ]),
        Shift.aggregate([
          { $match: { employer: employerId, assignedWorker: { $ne: null } } },
          {
            $group: {
              _id: '$assignedWorker',
              shifts: { $sum: 1 },
              earnings: { $sum: '$wage' }
            }
          },
          { $sort: { shifts: -1 } },
          { $limit: 5 },
          {
            $lookup: {
              from: 'users',
              localField: '_id',
              foreignField: '_id',
              as: 'worker'
            }
          },
          { $unwind: '$worker' },
          {
            $project: {
              _id: 0,
              workerId: '$_id',
              fullName: '$worker.fullName',
              rating: '$worker.rating',
              shifts: 1,
              earnings: 1
            }
          }
        ]),
        LeaveRequest.aggregate([{ $group: { _id: '$status', count: { $sum: 1 } } }]),
        SwapRequest.aggregate([{ $group: { _id: '$status', count: { $sum: 1 } } }])
      ]);

    const statusCount = (rows) =>
      rows.reduce(
        (acc, row) => {
          acc[row._id || 'unknown'] = row.count;
          return acc;
        },
        { open: 0, assigned: 0, completed: 0, cancelled: 0, pending: 0, approved: 0, rejected: 0 }
      );

    return res.json({
      message: 'Analytics fetched',
      shifts: {
        byStatus: statusCount(shiftsByStatus),
        wageTotals: shiftsByStatus.reduce((acc, row) => {
          acc[row._id || 'unknown'] = row.totalWage;
          return acc;
        }, {}),
        last7Days: shiftsLast7d
      },
      topSkills,
      topWorkers,
      leaves: statusCount(leaveStats),
      swaps: statusCount(swapStats)
    });
  } catch (err) {
    console.error('Employer analytics error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/analytics/worker
exports.workerAnalytics = async (req, res) => {
  try {
    const workerId = new mongoose.Types.ObjectId(req.user.id);

    const [byStatus, last30d, bySkill, appliedRecent] = await Promise.all([
      Shift.aggregate([
        { $match: { assignedWorker: workerId } },
        {
          $group: { _id: '$status', count: { $sum: 1 }, earnings: { $sum: '$wage' } }
        }
      ]),
      Shift.aggregate([
        {
          $match: {
            assignedWorker: workerId,
            status: 'completed',
            updatedAt: { $gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) }
          }
        },
        {
          $group: {
            _id: { $dateToString: { format: '%Y-%m-%d', date: '$updatedAt' } },
            earnings: { $sum: '$wage' },
            shifts: { $sum: 1 }
          }
        },
        { $sort: { _id: 1 } }
      ]),
      Shift.aggregate([
        { $match: { assignedWorker: workerId, status: 'completed' } },
        { $group: { _id: '$skillRequired', count: { $sum: 1 }, earnings: { $sum: '$wage' } } },
        { $sort: { count: -1 } },
        { $limit: 5 }
      ]),
      Shift.countDocuments({
        'applications.worker': workerId,
        createdAt: { $gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) }
      })
    ]);

    const byStatusMap = byStatus.reduce(
      (acc, row) => {
        acc.counts[row._id || 'unknown'] = row.count;
        acc.earnings[row._id || 'unknown'] = row.earnings;
        return acc;
      },
      {
        counts: { assigned: 0, completed: 0, cancelled: 0 },
        earnings: { assigned: 0, completed: 0, cancelled: 0 }
      }
    );

    const user = await User.findById(workerId).select(
      'rating completedShifts totalEarnings onTimeCompletionRate averageResponseTime'
    );

    return res.json({
      message: 'Worker analytics fetched',
      shifts: byStatusMap,
      last30Days: last30d,
      topSkills: bySkill,
      recentApplications: appliedRecent,
      profile: user
    });
  } catch (err) {
    console.error('Worker analytics error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

const ActivityFeed = require('../models/ActivityFeed');

exports.getFeed = async (req, res) => {
  try {
    const feed = await ActivityFeed.find()
      .populate('user', 'fullName profilePhoto role')
      .sort({ timestamp: -1 })
      .limit(50);
    return res.json({ feed });
  } catch (err) {
    console.error('Get feed error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

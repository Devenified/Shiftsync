const mongoose = require('mongoose');
const Message = require('../models/Message');
const User = require('../models/User');
const Notification = require('../models/Notification');

// POST /api/chat/send
exports.sendMessage = async (req, res) => {
  try {
    const { text, recipientId, teamId } = req.body;
    if (!text || (!recipientId && !teamId)) {
      return res
        .status(400)
        .json({ message: 'Text and at least a recipient or team id are required' });
    }

    const msg = await Message.create({
      sender: req.user.id,
      recipient: recipientId || undefined,
      teamId: teamId || undefined,
      text: String(text).trim()
    });

    if (recipientId) {
      try {
        const me = await User.findById(req.user.id).select('fullName');
        await Notification.create({
          userId: recipientId,
          message: `${me?.fullName || 'Someone'} sent you a message`,
          type: 'chat',
          relatedId: msg._id
        });
      } catch (e) {
        /* best-effort notification */
      }
    }

    const populated = await msg.populate('sender', 'fullName profilePhoto role');
    return res.status(201).json({ message: 'Message sent', msg: populated });
  } catch (err) {
    console.error('Send message error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/chat/messages?teamId=... OR ?otherUserId=...
exports.getMessages = async (req, res) => {
  try {
    const { teamId, otherUserId, limit = 100 } = req.query;
    let filter;

    if (teamId) {
      filter = { teamId };
    } else if (otherUserId) {
      filter = {
        $or: [
          { sender: req.user.id, recipient: otherUserId },
          { sender: otherUserId, recipient: req.user.id }
        ]
      };
    } else {
      return res.status(400).json({ message: 'teamId or otherUserId required' });
    }

    const messages = await Message.find(filter)
      .populate('sender', 'fullName profilePhoto role')
      .populate('recipient', 'fullName profilePhoto role')
      .sort({ createdAt: 1 })
      .limit(Math.min(Number(limit) || 100, 500));

    return res.json({ messages });
  } catch (err) {
    console.error('Get messages error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

// GET /api/chat/threads - list distinct users I've chatted with + last message
exports.listThreads = async (req, res) => {
  try {
    const me = new mongoose.Types.ObjectId(req.user.id);

    const threads = await Message.aggregate([
      {
        $match: {
          $or: [{ sender: me }, { recipient: me }],
          recipient: { $ne: null }
        }
      },
      {
        $addFields: {
          otherUser: {
            $cond: [{ $eq: ['$sender', me] }, '$recipient', '$sender']
          }
        }
      },
      { $sort: { createdAt: -1 } },
      {
        $group: {
          _id: '$otherUser',
          lastMessage: { $first: '$text' },
          lastAt: { $first: '$createdAt' },
          lastSender: { $first: '$sender' }
        }
      },
      {
        $lookup: {
          from: 'users',
          localField: '_id',
          foreignField: '_id',
          as: 'user'
        }
      },
      { $unwind: '$user' },
      {
        $project: {
          _id: 0,
          userId: '$_id',
          fullName: '$user.fullName',
          role: '$user.role',
          profilePhoto: '$user.profilePhoto',
          lastMessage: 1,
          lastAt: 1,
          lastSender: 1
        }
      },
      { $sort: { lastAt: -1 } }
    ]);

    return res.json({ threads });
  } catch (err) {
    console.error('List threads error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

const Message = require('../models/Message');

exports.sendMessage = async (req, res) => {
  try {
    const { text, recipientId, teamId } = req.body;
    const msg = await Message.create({
      sender: req.user.id,
      recipient: recipientId,
      teamId,
      text
    });
    return res.status(201).json({ message: 'Message sent', msg });
  } catch (err) {
    console.error('Send message error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

exports.getMessages = async (req, res) => {
  try {
    const { teamId } = req.query;
    const messages = await Message.find({ teamId })
      .populate('sender', 'fullName profilePhoto')
      .sort({ createdAt: 1 });
    return res.json({ messages });
  } catch (err) {
    console.error('Get messages error', err);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

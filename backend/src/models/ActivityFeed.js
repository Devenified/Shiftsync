const mongoose = require('mongoose');

const activityFeedSchema = new mongoose.Schema(
  {
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    action: { type: String, required: true }, // e.g., "swapped shift with Alex", "requested leave"
    type: {
      type: String,
      enum: ['shift_swap', 'leave_request', 'shift_assignment', 'announcement', 'attendance'],
      required: true
    },
    relatedId: { type: mongoose.Schema.Types.ObjectId }, // ID of the related swap, leave, shift, etc.
    timestamp: { type: Date, default: Date.now }
  },
  { timestamps: true }
);

module.exports = mongoose.model('ActivityFeed', activityFeedSchema);

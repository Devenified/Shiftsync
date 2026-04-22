const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    message: { type: String, required: true },
    type: {
      type: String,
      enum: [
        'shift_change',
        'shift_assignment',
        'shift_created',
        'shift_updated',
        'shift_cancelled',
        'swap_request',
        'swap_approved',
        'swap_rejected',
        'leave_request',
        'leave_approved',
        'leave_rejected',
        'announcement',
        'chat'
      ],
      required: true
    },
    read: { type: Boolean, default: false },
    relatedId: { type: mongoose.Schema.Types.ObjectId }
  },
  { timestamps: true }
);

module.exports = mongoose.model('Notification', notificationSchema);

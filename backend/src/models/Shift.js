const mongoose = require('mongoose');

const shiftApplicationSchema = new mongoose.Schema(
  {
    worker: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    status: {
      type: String,
      enum: ['pending', 'accepted', 'rejected'],
      default: 'pending'
    }
  },
  { _id: false, timestamps: true }
);

const shiftSchema = new mongoose.Schema(
  {
    employer: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    assignedWorker: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    title: { type: String, required: true, trim: true },
    description: { type: String, trim: true },
    skillRequired: { type: String, required: true, trim: true },
    location: { type: String, required: true, trim: true },
    shiftDate: { type: String, required: true, trim: true },
    startTime: { type: String, required: true, trim: true },
    endTime: { type: String, required: true, trim: true },
    wage: { type: Number, required: true, min: 0 },
    status: {
      type: String,
      enum: ['open', 'assigned', 'completed', 'cancelled'],
      default: 'open'
    },
    applications: [shiftApplicationSchema]
  },
  { timestamps: true }
);

module.exports = mongoose.model('Shift', shiftSchema);


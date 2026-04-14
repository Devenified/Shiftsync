const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    fullName: { type: String, required: true, trim: true },
    email: { type: String, required: true, unique: true, lowercase: true, trim: true },
    phoneNumber: { type: String, required: true, trim: true },
    companyName: {
      type: String,
      trim: true,
      required: function requiredCompanyName() {
        return this.role === 'employer';
      },
      default: ''
    },
    role: { type: String, required: true, enum: ['worker', 'employer'] },
    passwordHash: { type: String, required: true },
    hasProfile: { type: Boolean, default: false },
    
    // Worker Profile Details
    profilePhoto: { type: String, default: '' },
    dateOfBirth: { type: Date },
    gender: { type: String, enum: ['male', 'female', 'other', ''] },
    address: {
      street: { type: String, default: '' },
      city: { type: String, default: '' },
      state: { type: String, default: '' },
      pincode: { type: String, default: '' },
      landmark: { type: String, default: '' }
    },
    location: { type: String, trim: true },
    
    // Skills and Experience
    skills: [{ 
      type: String, 
      trim: true,
      required: function() { return this.role === 'worker'; }
    }],
    skillLevel: {
      type: String,
      enum: ['beginner', 'intermediate', 'expert', ''],
      default: ''
    },
    experienceYears: { type: Number, default: 0 },
    previousWork: [{
      companyName: { type: String, trim: true },
      position: { type: String, trim: true },
      startDate: { type: Date },
      endDate: { type: Date },
      description: { type: String, trim: true },
      achievements: [{ type: String, trim: true }]
    }],
    
    // Education
    education: [{
      institution: { type: String, trim: true },
      degree: { type: String, trim: true },
      field: { type: String, trim: true },
      startDate: { type: Date },
      endDate: { type: Date },
      isCompleted: { type: Boolean, default: true }
    }],
    
    // Professional Details
    bio: { type: String, trim: true, maxlength: 500 },
    languages: [{ type: String, trim: true }],
    certifications: [{
      name: { type: String, trim: true },
      issuer: { type: String, trim: true },
      issueDate: { type: Date },
      expiryDate: { type: Date },
      certificateUrl: { type: String, trim: true }
    }],
    
    // Work Preferences
    preferredWorkTypes: [{ type: String, trim: true }],
    preferredLocations: [{ type: String, trim: true }],
    expectedWage: { type: Number, default: 0 },
    wageNegotiable: { type: Boolean, default: true },
    workFlexibility: {
      type: String,
      enum: ['full-time', 'part-time', 'flexible', 'any', ''],
      default: ''
    },
    
    // Availability
    isAvailable: { type: Boolean, default: false },
    availableFrom: { type: Date },
    availableTo: { type: Date },
    preferredWorkHours: {
      type: String,
      enum: ['morning', 'afternoon', 'evening', 'night', 'flexible', ''],
      default: ''
    },
    
    // Verification and Trust
    isVerified: { type: Boolean, default: false },
    verificationDocuments: [{
      type: { type: String, trim: true }, // 'id', 'address', 'skill', etc.
      documentUrl: { type: String, trim: true },
      uploadDate: { type: Date, default: Date.now },
      status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' }
    }],
    
    // Social and Emergency
    emergencyContact: {
      name: { type: String, trim: true },
      relation: { type: String, trim: true },
      phoneNumber: { type: String, trim: true }
    },
    
    // Performance Metrics
    rating: { type: Number, default: 0, min: 0, max: 5 },
    totalReviews: { type: Number, default: 0 },
    completedShifts: { type: Number, default: 0 },
    totalEarnings: { type: Number, default: 0 },
    averageResponseTime: { type: Number, default: 0 }, // in minutes
    onTimeCompletionRate: { type: Number, default: 100 }, // percentage
    
    // Preferences
    notifications: {
      email: { type: Boolean, default: true },
      sms: { type: Boolean, default: true },
      push: { type: Boolean, default: true }
    },
    
    // Account Status
    isActive: { type: Boolean, default: true },
    lastLogin: { type: Date, default: Date.now },
    loginCount: { type: Number, default: 0 }
  },
  { timestamps: true }
);

module.exports = mongoose.model('User', userSchema);


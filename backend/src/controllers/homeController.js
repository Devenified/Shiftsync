exports.getHome = (req, res) => {
  res.json({
    message: 'Welcome to Shiftsync backend!',
    status: 'ok'
  });
};

exports.healthCheck = (req, res) => {
  res.json({
    message: 'Frontend and backend connection OK',
    status: 'ok'
  });
  console.log('Health check OK');
};


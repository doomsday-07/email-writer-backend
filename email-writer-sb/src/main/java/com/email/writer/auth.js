const express = require('express');
const { OAuth2Client } = require('google-auth-library');
const router = express.Router();

// Make sure to configure your .env file with your GOOGLE_CLIENT_ID
const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

router.post('/google', async (req, res) => {
  const { token } = req.body;
  
  if (!token) {
    return res.status(400).json({ success: false, message: 'Token is required' });
  }

  try {
    // Verify the token with Google
    const ticket = await client.verifyIdToken({
      idToken: token,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    
    const payload = ticket.getPayload();
    
    // payload contains the user's details
    const { sub: userid, email, name, picture } = payload;

    // TODO: Find or create the user in your database, and establish a session/JWT.

    res.status(200).json({ success: true, user: { userid, email, name, picture } });
  } catch (error) {
    console.error('Error verifying Google token:', error);
    res.status(401).json({ success: false, message: 'Invalid token' });
  }
});

module.exports = router;
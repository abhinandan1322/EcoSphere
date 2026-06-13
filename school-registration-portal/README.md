# School Registration Portal

A professional web portal for schools to register and receive their unique access codes for the EcoSphere Platform.

## Features

✅ **Professional Registration Form**
- School information (name, type, location)
- Contact details (principal, email, phone)
- Additional details (student/teacher count, website)
- Terms and conditions acceptance

✅ **Automatic Code Generation**
- Generates unique 14-character school codes
- Format: `SCHOOLNAME + CITY + YEAR + RANDOM`
- Example: `DELHIPUBIN2026MK`

✅ **Firebase Integration**
- Automatically creates school in Firestore
- Validates duplicate registrations
- Real-time data sync with mobile app

✅ **User-Friendly Features**
- Success modal with generated code
- Copy to clipboard functionality
- Download registration details as text file
- Responsive design (mobile-friendly)
- Form validation

## Setup Instructions

### 1. Get Your Firebase Configuration

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Go to **Project Settings** (gear icon)
4. Scroll down to "Your apps" section
5. Click on the **Web app** (</> icon)
6. Copy the `firebaseConfig` object

### 2. Update Firebase Configuration

Open `firebase-config.js` and replace with your actual Firebase config:

```javascript
const firebaseConfig = {
    apiKey: "AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
    authDomain: "your-project.firebaseapp.com",
    projectId: "your-project-id",
    storageBucket: "your-project.appspot.com",
    messagingSenderId: "123456789012",
    appId: "1:123456789012:web:abcdef123456"
};
```

### 3. Deploy the Portal

#### Option A: Firebase Hosting (Recommended)

1. Install Firebase CLI:
   ```bash
   npm install -g firebase-tools
   ```

2. Login to Firebase:
   ```bash
   firebase login
   ```

3. Initialize Firebase Hosting:
   ```bash
   cd school-registration-portal
   firebase init hosting
   ```
   - Select your Firebase project
   - Set public directory to: `.` (current directory)
   - Configure as single-page app: **No**
   - Don't overwrite existing files

4. Deploy:
   ```bash
   firebase deploy --only hosting
   ```

5. Your portal will be live at: `https://your-project.web.app`

#### Option B: Local Testing

1. Open `index.html` in a web browser
2. Or use a local server:
   ```bash
   # Python 3
   python -m http.server 8000
   
   # Node.js (install http-server first)
   npx http-server
   ```
3. Open `http://localhost:8000` in your browser

#### Option C: Other Hosting Services

You can also deploy to:
- **Netlify**: Drag and drop the folder
- **Vercel**: Connect GitHub repo
- **GitHub Pages**: Push to gh-pages branch

### 4. Update Firestore Security Rules

Make sure your Firestore rules allow public read access to Schools collection (already done in previous steps):

```javascript
match /Schools/{schoolId} {
  allow read: if true;
  allow write: if isAdmin();
}
```

## Usage

### For Platform Administrators

1. Share the portal URL with schools/organizations
2. Schools fill out the registration form
3. System generates unique school code
4. School receives code via email (if email service configured)
5. School can download registration details

### For Schools

1. Visit the registration portal
2. Fill out all required fields
3. Accept terms and conditions
4. Click "Generate School Code"
5. Copy the generated code
6. Download registration details for records
7. Share code with authorized teachers

### For Teachers

1. Teachers receive school code from their school
2. Download EcoSphere mobile app
3. Select "Teacher" role
4. Enter school code during registration
5. First teacher becomes Lead Teacher (auto-approved)
6. Subsequent teachers need Lead Teacher approval

## File Structure

```
school-registration-portal/
├── index.html           # Main HTML file
├── styles.css          # Styling
├── firebase-config.js  # Firebase configuration
├── app.js             # Application logic
└── README.md          # This file
```

## Customization

### Change Color Scheme

Edit `styles.css` and update the gradient colors:

```css
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
```

### Add Email Notifications

To send emails when schools register, you'll need to:

1. Set up Firebase Cloud Functions
2. Use a service like SendGrid, Mailgun, or Firebase Extensions
3. Add email sending logic in `app.js`

Example with Firebase Cloud Functions:

```javascript
// In app.js, after successful registration
await fetch('https://your-region-your-project.cloudfunctions.net/sendSchoolCode', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        email: formData.contactEmail,
        schoolName: formData.schoolName,
        schoolCode: schoolCode
    })
});
```

### Modify Form Fields

Edit `index.html` to add/remove fields as needed. Make sure to update:
1. HTML form fields
2. JavaScript form data collection in `app.js`
3. Firestore document structure

## Security Considerations

✅ **Current Security**
- Form validation (client-side)
- Duplicate school code prevention
- Firestore security rules (server-side)

⚠️ **Recommended Additions**
- Add reCAPTCHA to prevent spam
- Implement rate limiting
- Add admin approval workflow
- Set up email verification

## Troubleshooting

### Firebase Not Connecting

1. Check browser console for errors
2. Verify Firebase config is correct
3. Ensure Firestore is enabled in Firebase Console
4. Check Firestore security rules

### School Code Not Generating

1. Check browser console for errors
2. Verify Firestore write permissions
3. Check if Schools collection exists

### Form Not Submitting

1. Check all required fields are filled
2. Verify email format is correct
3. Check browser console for validation errors

## Support

For issues or questions:
- Email: support@EcoSphere.edu
- Check browser console for error messages
- Review Firestore security rules

## License

© 2026 EcoSphere Platform. All rights reserved.

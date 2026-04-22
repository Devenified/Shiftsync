## 🔧 USB DEBUGGING CONNECTION TROUBLESHOOTING GUIDE

### **🚀 Current Status:**
- **Backend Server:** ✅ Running on port 3000
- **Computer IP:** ✅ 10.87.0.168 (Wi-Fi)
- **App Updated:** ✅ With connection testing

### **🔍 Step-by-Step Troubleshooting:**

#### **1. Check Backend Server:**
```bash
cd "c:\Users\singa\OneDrive\Desktop\Shiftsync\backend"
npm start
```
**Should show:** `Server running on http://localhost:3000`

#### **2. Test Server Access:**
Open browser on computer: `http://10.87.0.168:3000/api/users`
**Should show:** JSON response or "Not found" (not connection error)

#### **3. Check Windows Firewall:**
- Open Windows Security → Firewall & network protection
- Click "Allow an app through firewall"
- Find "Node.js" or add port 3000 manually
- Allow both Private and Public networks

#### **4. Verify Mobile Network:**
- Mobile device must be on same Wi-Fi network as computer
- Check mobile Wi-Fi: Settings → Wi-Fi → Connected network
- Should be same network as your computer's Wi-Fi

#### **5. Test Mobile App:**
- Open ShiftSync app
- Try to login
- **New Feature:** App will now show detailed error message

### **📱 What the App Will Show You:**

#### **✅ If Connection Works:**
- App proceeds to login normally
- Shows "Connecting..." then logs in

#### **❌ If Connection Fails:**
- Shows detailed error message:
```
Connection error: Cannot reach server

IP: http://10.87.0.168:3000

Check:
• Backend is running
• Computer IP is correct  
• Firewall allows port 3000
```

### **🔧 Alternative Solutions:**

#### **Option A: Different IP Address**
If your IP changes, update both files:
```java
// In ApiClient.java and SessionManager.java
public static final String BASE_URL = "http://NEW_IP:3000";
```

#### **Option B: Use Emulator Instead**
- Use Android Studio emulator
- Change BASE_URL to: `http://10.0.2.2:3000`

#### **Option C: Network Reset**
1. Restart backend server
2. Restart mobile device Wi-Fi
3. Reconnect USB debugging

### **🌐 Network Configuration Check:**

#### **Computer Network:**
- ✅ Wi-Fi connected: 10.87.0.168
- ✅ Backend listening: 0.0.0.0:3000
- ❓ Firewall: May block port 3000

#### **Mobile Device:**
- ❓ Same Wi-Fi network?
- ❓ Can reach 10.87.0.168:3000?
- ❓ USB debugging enabled?

### **🔍 Debug Commands:**

#### **Check Server Status:**
```bash
netstat -an | findstr "3000"
# Should show: TCP 0.0.0.0:3000 LISTENING
```

#### **Test Connection:**
```bash
curl http://10.87.0.168:3000/api
# Should return: {"message":"Not found"}
```

#### **Check IP Address:**
```bash
ipconfig
# Look for: IPv4 Address. . . : 10.87.0.168
```

### **📞 Next Steps:**

1. **Try the updated app** - It will show you exactly what's wrong
2. **Check firewall** - Most common issue
3. **Verify same Wi-Fi** - Mobile must be on same network
4. **Test with browser** - Confirm server accessible from computer

**The updated app now tells you exactly what the connection problem is!** 🇮🇳

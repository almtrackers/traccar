# Voice ID and Web Monitoring Enhancements

## ✅ Completed Enhancements

### 1. Event-Based Voice ID System
**Problem Solved**: Voice ID is now dependent on alert type, allowing different voices for different alerts.

#### **Default Voice ID Mapping**
- `deviceOffline` → Voice ID 211
- `deviceOnline` → Voice ID 212
- `deviceOverspeed` → Voice ID 213
- `geofenceEnter` → Voice ID 214
- `geofenceExit` → Voice ID 215
- `alarm` → Voice ID 216
- `ignitionOn` → Voice ID 217
- `ignitionOff` → Voice ID 218
- `maintenance` → Voice ID 219
- `textMessage` → Voice ID 220
- `driverChanged` → Voice ID 221
- `deviceMoving` → Voice ID 222
- `deviceStopped` → Voice ID 223
- `deviceInactive` → Voice ID 224
- `fuelDrop` → Voice ID 225
- `powerCut` → Voice ID 226
- `powerRestored` → Voice ID 227
- All other events → Voice ID 210 (default)

#### **Custom Voice ID Configuration**
You can override the default mappings using device or user attributes:

**Device-Specific Event Mapping:**
```json
{
  "robocallVoiceId.deviceOffline": "301",
  "robocallVoiceId.alarm": "302"
}
```

**User-Specific Event Mapping:**
```json
{
  "robocallVoiceId.geofenceExit": "401",
  "robocallVoiceId.deviceOverspeed": "402"
}
```

#### **Voice ID Priority System**
1. **Event-specific device mapping** (`robocallVoiceId.{eventType}` in device attributes)
2. **Event-specific user mapping** (`robocallVoiceId.{eventType}` in user attributes)
3. **Default event type mapping** (built-in table above)
4. **Event attributes** (`robocallVoiceId`)
5. **Device attributes** (`robocallVoiceId`)
6. **User attributes** (`robocallVoiceId`)
7. **System default** ("210")

### 2. Web Monitoring Interface
**Problem Solved**: Real-time web interface for monitoring robocall logs.

#### **Access URL**
```
https://your-traccar-domain/robocall-logs
```

#### **Features**
- **📊 Real-time Statistics Dashboard**
  - Total calls counter
  - Answered calls (green)
  - Failed calls (red)
  - Pending calls (yellow)
  - Retried calls (blue)

- **🔍 Advanced Filtering**
  - Date/time range picker
  - Call status filter (answered, failed, no-answer, busy, initiated)
  - Voice ID filter
  - Real-time search and filtering

- **📋 Detailed Call Logs Table**
  - Date/time of call
  - Device information
  - Phone number called
  - Voice ID used
  - Vehicle number
  - Call status with color-coded badges
  - Call duration
  - DTMF response
  - Retry count

- **⚡ Real-time Updates**
  - Auto-refresh every 30 seconds
  - Live status updates
  - Pagination support

- **📱 Responsive Design**
  - Works on desktop and mobile
  - Touch-friendly interface
  - Modern, clean design

#### **Enhanced API Endpoints**

**Robocall Logs with Advanced Filtering:**
```
GET /api/robocall/logs?from=2024-01-01 00:00:00&to=2024-12-31 23:59:59&callStatus=answered&voiceId=211&limit=50
```

**Statistics API:**
```
GET /api/robocall/logs/stats
```
Returns:
```json
{
  "totalCalls": 150,
  "answeredCalls": 120,
  "failedCalls": 20,
  "pendingCalls": 5,
  "unknownCalls": 2,
  "retriedCalls": 15
}
```

## 🔧 Technical Implementation

### Files Modified/Added

**New Files:**
- `src/main/resources/robocall-logs.html` - Web monitoring interface
- `src/main/java/org/traccar/web/RobocallLogsServlet.java` - Servlet to serve the HTML

**Enhanced Files:**
- `src/main/java/org/traccar/notificators/NotificatorRobocall.java` - Added event-based voice ID logic
- `src/main/java/org/traccar/api/resource/RobocallLogResource.java` - Enhanced with filtering and statistics
- `src/main/java/org/traccar/web/WebModule.java` - Registered new servlet
- `src/test/java/org/traccar/notificators/NotificatorRobocallTest.java` - Added tests for voice ID mapping

## 🚀 Usage Examples

### Example 1: Different Voice for Device Offline
When a device goes offline, it will automatically use Voice ID 211 instead of the default 210.

### Example 2: Custom Voice for Specific Vehicle
Device attributes:
```json
{
  "robocallVoiceId.alarm": "999",
  "numberPlate": "ABC-123"
}
```
When this vehicle triggers an alarm, it will use Voice ID 999.

### Example 3: User-Specific Voice Mapping
User attributes:
```json
{
  "robocallVoiceId.geofenceExit": "888"
}
```
All geofence exit events for this user will use Voice ID 888.

## 📈 Monitoring and Analytics

### Real-time Dashboard Metrics
- **Success Rate**: Percentage of answered calls
- **Failure Analysis**: Breakdown of failed call reasons
- **Voice ID Usage**: Track which voice IDs are used most
- **Retry Patterns**: Monitor retry success rates

### Filtering Capabilities
- Filter by specific time periods
- Filter by call status to analyze failures
- Filter by voice ID to track specific message usage
- Filter by device to monitor specific vehicles

## ✅ Testing

Added comprehensive tests for:
- Event-type based voice ID selection
- Custom voice ID mapping from device attributes
- Priority system for voice ID selection
- Web interface functionality

## 🔒 Security and Performance

- Web interface uses proper caching headers
- API endpoints respect user permissions
- Statistics calculations are optimized
- Auto-refresh can be configured or disabled

## 📚 Documentation

Complete documentation updated in `ROBOCALL_CONFIGURATION.md` including:
- Event-based voice ID configuration guide
- Web monitoring interface usage
- API endpoint documentation
- Troubleshooting guide with web interface tips

## 🎯 Result

The robocall system now provides:
1. **✅ Event-dependent voice IDs** - Different voices for different alert types
2. **✅ Professional web monitoring interface** - Real-time dashboard for call logs
3. **✅ Advanced filtering and analytics** - Comprehensive monitoring capabilities
4. **✅ Flexible configuration system** - Easy customization via device/user attributes

Both requirements are fully implemented and ready for production use!
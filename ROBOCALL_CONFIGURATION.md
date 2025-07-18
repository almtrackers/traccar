# Robocall Notification Configuration

This document describes how to configure the Robocall notification system for Traccar to integrate with Robocall.pk.

## Features

- **Voice Calls**: Send automated voice calls when alerts are triggered
- **Event-Based Voice IDs**: Different voice messages for different alert types
- **Database Logging**: Track all robocall attempts, statuses, and responses
- **Web Monitoring**: Real-time monitoring page with filtering and statistics
- **Retry Mechanism**: Automatically retry failed calls (up to 3 attempts with 5-minute intervals)
- **Webhook Integration**: Receive status updates from Robocall.pk
- **Flexible Configuration**: Configure voice IDs and vehicle numbers via device/user attributes

## Configuration

Add the following entries to your `traccar.xml` configuration file:

```xml
<!-- Enable robocall notificator -->
<entry key='notificator.types'>web,mail,sms,robocall</entry>

<!-- Robocall API Configuration -->
<entry key='notificator.robocall.apiKey'>YOUR_ROBOCALL_API_KEY</entry>
<entry key='notificator.robocall.url'>https://portal.robocall.pk/api/calls</entry>
```

## Database Setup

The robocall functionality requires a new database table. The migration will be applied automatically when you start Traccar with the updated code.

The `robocall_log` table includes the following fields:
- `id`: Primary key
- `rcId`: Robocall.pk call ID
- `callTo`: Phone number called
- `voiceId`: Voice ID used for the call
- `vehicleNumber`: Vehicle identifier
- `callStatus`: Current call status
- `dtmf`: DTMF response from caller
- `duration`: Call duration in seconds
- `deviceId`: Related device ID
- `eventId`: Related event ID
- `userId`: User who received the call
- `createdAt`: Call initiation timestamp
- `updatedAt`: Last update timestamp
- `retryCount`: Number of retry attempts
- `nextRetryAt`: Next retry timestamp

## Event-Based Voice ID Configuration

The system automatically assigns different voice IDs based on the alert type:

### Default Voice ID Mapping

| Event Type | Voice ID | Description |
|------------|----------|-------------|
| `deviceOffline` | 211 | Device went offline |
| `deviceOnline` | 212 | Device came online |
| `deviceOverspeed` | 213 | Speed limit exceeded |
| `geofenceEnter` | 214 | Entered geofence |
| `geofenceExit` | 215 | Exited geofence |
| `alarm` | 216 | General alarm |
| `ignitionOn` | 217 | Ignition turned on |
| `ignitionOff` | 218 | Ignition turned off |
| `maintenance` | 219 | Maintenance due |
| `textMessage` | 220 | Text message received |
| `driverChanged` | 221 | Driver changed |
| `deviceMoving` | 222 | Device started moving |
| `deviceStopped` | 223 | Device stopped |
| `deviceInactive` | 224 | Device inactive |
| `fuelDrop` | 225 | Fuel level dropped |
| `powerCut` | 226 | Power disconnected |
| `powerRestored` | 227 | Power restored |
| Others | 210 | Default voice |

### Custom Voice ID Configuration

You can override voice IDs using device or user attributes:

#### Device-Specific Event Voice IDs
```json
{
  "robocallVoiceId.deviceOffline": "301",
  "robocallVoiceId.geofenceExit": "302"
}
```

#### User-Specific Event Voice IDs
```json
{
  "robocallVoiceId.alarm": "401",
  "robocallVoiceId.deviceOverspeed": "402"
}
```

## Device/User Attributes

### Device Attributes
- `robocallVoiceId.{eventType}`: Custom voice ID for specific event type
- `robocallVoiceId`: Default voice ID for all events from this device
- `numberPlate`: Vehicle number plate (used as text1 in API call)
- `vehicleNumber`: Alternative vehicle identifier
- `expiryDate`: Expiry date for the vehicle

### User Attributes
- `robocallVoiceId.{eventType}`: Custom voice ID for specific event type for this user
- `robocallVoiceId`: Default voice ID for this user
- `phone`: User's phone number (required for receiving robocalls)

### Event Attributes
- `robocallVoiceId`: Override voice ID for this specific event instance

## Voice ID Priority

The system uses the following priority order for voice ID selection:
1. **Event-specific device mapping** (`robocallVoiceId.{eventType}` in device attributes)
2. **Event-specific user mapping** (`robocallVoiceId.{eventType}` in user attributes)
3. **Default event type mapping** (built-in mapping table)
4. **Event attributes** (`robocallVoiceId`)
5. **Device attributes** (`robocallVoiceId`)
6. **User attributes** (`robocallVoiceId`)
7. **System default** ("210")

## Web Monitoring Interface

Access the robocall logs monitoring page at:
```
https://your-traccar-domain/robocall-logs
```

### Features:
- **Real-time Statistics**: Total, answered, failed, pending, and retried calls
- **Advanced Filtering**: By date range, call status, voice ID
- **Pagination**: Browse through large datasets
- **Auto-refresh**: Updates every 30 seconds
- **Responsive Design**: Works on desktop and mobile devices

### Monitoring Dashboard Includes:
- Call success/failure rates
- Recent call logs with detailed information
- Filter by device, user, status, or time period
- Export capabilities for reporting

## API Endpoints

### Webhook Endpoint
Configure Robocall.pk to send status updates to:
```
POST /api/webhook/robocall
```

Parameters:
- `rc_id`: Robocall ID
- `call_status`: Call status (answered, failed, no-answer, busy, etc.)
- `dtmf`: DTMF response from caller
- `duration`: Call duration in seconds

### Robocall Logs API
View robocall logs via the API:
```
GET /api/robocall/logs?deviceId=123&userId=456&limit=100&from=2024-01-01 00:00:00&to=2024-12-31 23:59:59&callStatus=answered&voiceId=211
```

### Statistics API
Get robocall statistics:
```
GET /api/robocall/logs/stats
```

## Notification Setup

1. Create a notification in Traccar admin panel
2. Set the notification type to the event you want to monitor
3. In the "Notificators" field, include "robocall"
4. Ensure the user has a valid phone number configured
5. Optionally configure custom voice IDs via device/user attributes

## Vehicle Number Priority

The system uses the following priority for vehicle number:
1. Device attribute (`numberPlate`)
2. Device attribute (`vehicleNumber`)
3. Device name
4. "Unknown"

## Retry Logic

- Failed calls are automatically retried up to 3 times
- Retry interval is 5 minutes between attempts
- Retries are triggered for calls with status: `null`, `""`, `"failed"`, `"no-answer"`, `"busy"`
- Successful calls (`"answered"`) are not retried

## Troubleshooting

### Check Configuration
Ensure your API key and URL are correctly configured in `traccar.xml`.

### Verify Phone Numbers
Make sure user phone numbers are in the correct format (e.g., "923001234567" or "+923001234567").

### Monitor Web Interface
Use the web monitoring interface at `/robocall-logs` to track call status and debug issues.

### Check Voice ID Mappings
Verify that voice IDs are correctly mapped for different event types by checking the device/user attributes.

### Monitor Logs
Check the Traccar logs for robocall-related messages:
```bash
tail -f logs/tracker-server.log | grep -i robocall
```

### Database Queries
Query the robocall_log table to check call history:
```sql
SELECT * FROM robocall_log ORDER BY createdAt DESC LIMIT 10;
```

## Example Configuration

Complete example configuration for `traccar.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM 'http://java.sun.com/dtd/properties.dtd'>
<properties>
    <!-- Database Configuration -->
    <entry key='database.driver'>com.mysql.cj.jdbc.Driver</entry>
    <entry key='database.url'>jdbc:mysql://localhost:3306/traccar?serverTimezone=UTC&amp;useSSL=false&amp;allowMultiQueries=true&amp;autoReconnect=true&amp;useUnicode=true&amp;characterEncoding=utf8</entry>
    <entry key='database.user'>traccar</entry>
    <entry key='database.password'>password</entry>

    <!-- Enable Notifications -->
    <entry key='notificator.types'>web,mail,sms,robocall</entry>
    
    <!-- Robocall Configuration -->
    <entry key='notificator.robocall.apiKey'>your_robocall_api_key_here</entry>
    <entry key='notificator.robocall.url'>https://portal.robocall.pk/api/calls</entry>
</properties>
```

## Security Considerations

- Store your Robocall.pk API key securely
- Consider restricting webhook endpoint access by IP
- Monitor the robocall logs web interface for unauthorized usage
- Implement rate limiting if needed to prevent abuse
- Secure access to the `/robocall-logs` monitoring page with authentication
# Traccar Robocall Integration

This document provides comprehensive information about the new robocall notification feature in Traccar that enables automatic phone calls via the Robocall.pk API when specific events occur.

## Overview

The robocall notificator allows Traccar to automatically trigger phone calls to users when device events occur (such as device going offline, geofence violations, speed violations, etc.). The calls are made through the Robocall.pk API service.

## Features

- **Automatic Robocalls**: Trigger robocalls based on any Traccar event
- **Dynamic Content**: Customize call content with vehicle information and event details
- **Voice Selection**: Configure different voice IDs for different users/devices
- **Caller ID Configuration**: Set custom caller IDs per user
- **Flexible Text Content**: Include vehicle number and expiry/event dates in calls
- **Error Handling**: Comprehensive logging and error reporting
- **Rate Limiting Ready**: Built-in support for preventing spam calls

## Configuration

### 1. Basic Configuration

Add the following configuration to your `traccar.xml` file:

```xml
<!-- Enable robocall notificator -->
<entry key='notificator.types'>web,mail,sms,robocall</entry>

<!-- Robocall API Configuration -->
<entry key='notificator.robocall.apiKey'>YOUR_API_KEY_HERE</entry>
<entry key='notificator.robocall.url'>https://portal.robocall.pk/api/calls</entry>
```

### 2. Configuration Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `notificator.robocall.apiKey` | Your Robocall.pk API key | Yes | None |
| `notificator.robocall.url` | Robocall API endpoint URL | No | https://portal.robocall.pk/api/calls |

### 3. User Configuration

Each user that should receive robocalls must have a phone number configured:

1. Go to **Settings > Users**
2. Edit the user
3. Set the **Phone** field with the complete phone number (e.g., `923001234567`)
4. Save the user

**Phone Number Format:**
- Include country code (e.g., `923001234567` for Pakistan)
- Or use international format with + (e.g., `+923001234567`)
- Numbers without country code will default to Pakistan (`92`)

### 4. Voice ID Configuration

Configure different voice IDs for different events and contexts. The system supports multiple levels of voice ID configuration:

#### Per-Event Voice ID (Highest Priority)
Configure specific voice IDs when creating notifications in the Traccar web interface:

1. Go to **Settings > Notifications**
2. Create or edit a notification
3. Set the **Voice ID** field (e.g., `210`, `215`, `220`)
4. Save the notification

This allows different events (device offline, geofence violation, speed violation, etc.) to use different voices.

#### Device-Level Voice ID
```xml
<!-- In device attributes -->
<entry key="robocallVoiceId">210</entry>
```

#### User-Level Voice ID
```xml
<!-- In user attributes -->
<entry key="robocallVoiceId">210</entry>
```

#### Event Runtime Voice ID
```xml
<!-- In event attributes (programmatically set) -->
<entry key="robocallVoiceId">210</entry>
```

**Priority Order:** Notification voiceId > Event attributes > Device attributes > User attributes > Default (210)

### 5. Vehicle Information Configuration

Configure vehicle-specific information that will be included in robocalls:

#### Vehicle Number/Name
```xml
<!-- Device attributes (priority order) -->
<entry key="numberPlate">LEV6485</entry>
<entry key="vehicleNumber">LEV6485</entry>
<!-- Falls back to device name if not set -->
```

#### Expiry Date
```xml
<!-- Device attributes -->
<entry key="expiryDate">2024-05-10</entry>
<!-- Can be Date object or String -->
```

## API Call Format

The robocall notificator makes HTTP GET requests to the Robocall.pk API with the following format:

```
https://portal.robocall.pk/api/calls?api_key={API_KEY}&caller_id={PHONE}&voice_id={VOICE_ID}&text1={VEHICLE}&text2={EXPIRY_DATE}
```

### Parameters

| Parameter | Source | Example | Description |
|-----------|--------|---------|-------------|
| `api_key` | Configuration | `0nCY2tgeMMZEhvr9KKLqipnZHhP7WR99` | Static API key |
| `caller_id` | User.phone | `923001234567` | User's phone number |
| `voice_id` | Notification/Attributes | `210` | Voice ID for the call (per-event configurable) |
| `text1` | Device info | `LEV6485` | Vehicle number/name |
| `text2` | Event/Device info | `10th of May` | Expiry date or event date |

## Setting Up Notifications

### 1. Create a Notification

1. Go to **Settings > Notifications**
2. Click **Add** to create a new notification
3. Configure the notification:
   - **Type**: Select events you want to trigger robocalls
   - **Notificators**: Check **robocall**
   - **Users**: Select users who should receive calls
   - **Devices**: Select devices that should trigger calls

### 2. Advanced Configuration

For more control, you can set event-specific voice IDs by adding custom attributes to notifications or events.

## Testing

### Test Configuration
```xml
<!-- Add test configuration -->
<entry key='notificator.robocall.apiKey'>test-api-key</entry>
<entry key='notificator.robocall.url'>https://httpbin.org/get</entry>
```

### Generate Test Event
1. Create a test notification for "Device Offline" events
2. Take a device offline to trigger the notification
3. Check logs for API call details

## Troubleshooting

### Common Issues

1. **"User phone number not configured"**
   - Solution: Set the phone number in user settings

2. **"Device not found"**
   - Solution: Check that the device exists and is accessible

3. **"Robocall API request failed"**
   - Solution: Check API key, internet connectivity, and API status

4. **No calls received**
   - Solution: Verify phone number format and check API logs

### Logging

The robocall notificator logs detailed information about each API call:

```
INFO  - Making robocall API request: https://portal.robocall.pk/api/calls?...
INFO  - Robocall API request successful for user: John Doe, device: Vehicle-001
ERROR - Robocall API request failed with status: 401, body: Invalid API key
```

Check your Traccar logs for these messages to troubleshoot issues.

### Log Levels

- **INFO**: Successful API calls and request details
- **ERROR**: API failures, missing configuration, validation errors
- **DEBUG**: Detailed parameter extraction and URL building

## Security Considerations

1. **API Key Protection**: Store your API key securely and don't expose it in logs
2. **Rate Limiting**: Consider implementing rate limiting to prevent spam calls
3. **Phone Number Validation**: Ensure phone numbers are properly formatted
4. **Access Control**: Limit who can configure robocall notifications

## Performance Notes

- Robocall API calls are made synchronously during event processing
- Failed API calls are logged but don't block other notifications
- Consider the impact on event processing latency
- The HTTP client includes proper timeout handling

## API Integration Details

### Request Format
- **Method**: HTTP GET
- **Encoding**: URL parameters are properly URL-encoded
- **Timeout**: Uses standard Jakarta WS-RS client timeout settings
- **Error Handling**: HTTP status codes 200-299 are considered successful

### Response Handling
- Success: HTTP 200-299 status codes
- Failure: All other status codes with response body logging
- Network errors are caught and logged appropriately

## Example Scenarios

### Scenario 1: Vehicle License Expiry
- **Event**: Custom event for license expiry
- **text1**: Vehicle number from `numberPlate` attribute
- **text2**: Expiry date from `expiryDate` attribute
- **Result**: Call saying "Vehicle LEV6485 license expires on 10th of May"

### Scenario 2: Device Offline Alert
- **Event**: Device offline
- **text1**: Device name
- **text2**: Current date
- **Result**: Call about vehicle going offline

### Scenario 3: Geofence Violation
- **Event**: Geofence exit
- **text1**: Vehicle identifier
- **text2**: Event timestamp
- **Result**: Alert about unauthorized movement

## Migration and Upgrades

When upgrading Traccar:
1. Backup your configuration files
2. The robocall notificator is backward compatible
3. No database migrations are required
4. Existing notifications will continue to work

## Support and Maintenance

- The robocall integration is built using standard Traccar notificator patterns
- Regular updates will be included in Traccar releases
- For API issues, contact Robocall.pk support
- For integration issues, check Traccar community forums

## Contributing

To contribute improvements to the robocall integration:
1. Follow Traccar's coding standards
2. Add appropriate tests for new features
3. Update documentation for any changes
4. Submit pull requests with clear descriptions

---

For more information about Traccar notifications in general, see the official Traccar documentation.
/*
 * Copyright 2024 Traccar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.notificators;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.RobocallRetryManager;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.RobocallLog;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

@Singleton
public class NotificatorRobocall extends Notificator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificatorRobocall.class);

    private final Client client;
    private final Storage storage;
    private final RobocallRetryManager retryManager;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    @Inject
    public NotificatorRobocall(
            Config config,
            NotificationFormatter notificationFormatter,
            Client client,
            Storage storage,
            RobocallRetryManager retryManager) {
        super(notificationFormatter, "short");
        this.client = client;
        this.storage = storage;
        this.retryManager = retryManager;
        this.objectMapper = new ObjectMapper();
        this.apiKey = config.getString(Keys.NOTIFICATOR_ROBOCALL_API_KEY);
        this.baseUrl = config.getString(Keys.NOTIFICATOR_ROBOCALL_URL, "https://portal.robocall.pk/api/calls");
    }

    @Override
    public void send(User user, NotificationMessage message, Event event, Position position) throws MessageException {
        try {
            // Get caller ID from user phone number
            String callerId = getCallerId(user);
            if (callerId == null) {
                throw new MessageException("User phone number not configured for robocall");
            }

            // Get device for extracting vehicle information
            Device device = getDevice(event.getDeviceId());
            if (device == null) {
                throw new MessageException("Device not found for robocall");
            }

            makeRobocall(user, device, event, position, null);

        } catch (Exception e) {
            LOGGER.error("Error sending robocall notification", e);
            throw new MessageException("Failed to send robocall: " + e.getMessage());
        }
    }

    public void retryCall(User user, Device device, Event event, Position position, RobocallLog existingLog) throws MessageException {
        makeRobocall(user, device, event, position, existingLog);
    }

    private void makeRobocall(User user, Device device, Event event, Position position, RobocallLog existingLog) throws MessageException {
        try {
            String callerId = getCallerId(user);
            String voiceId = getVoiceId(event, device, user);
            String text1 = getVehicleNumber(device);
            String text2 = getExpiryDate(device, event);

            // Build the API URL
            String apiUrl = buildApiUrl(callerId, voiceId, text1, text2);

            LOGGER.info("Making robocall API request: {}", apiUrl);

            // Make the HTTP GET request
            try (Response response = client.target(apiUrl).request().get()) {
                String responseBody = response.readEntity(String.class);
                
                if (response.getStatus() >= 200 && response.getStatus() < 300) {
                    LOGGER.info("Robocall API request successful for user: {}, device: {}, response: {}",
                               user.getName(), device.getName(), responseBody);
                    
                    // Parse the response and save to database
                    saveRobocallLog(responseBody, callerId, voiceId, text1, user, device, event, existingLog);
                    
                } else {
                    LOGGER.error("Robocall API request failed with status: {}, body: {}",
                                response.getStatus(), responseBody);
                    
                    // Save failed attempt to database
                    saveFailedRobocallLog(callerId, voiceId, text1, user, device, event, 
                                        "API request failed with status: " + response.getStatus(), existingLog);
                    
                    throw new MessageException("Robocall API request failed: " + response.getStatus());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error making robocall", e);
            
            try {
                // Save failed attempt to database
                saveFailedRobocallLog(getCallerId(user), getVoiceId(event, device, user), 
                                    getVehicleNumber(device), user, device, event, e.getMessage(), existingLog);
            } catch (Exception dbError) {
                LOGGER.error("Error saving failed robocall log", dbError);
            }
            
            throw new MessageException("Failed to make robocall: " + e.getMessage());
        }
    }

    private void saveRobocallLog(String responseBody, String callerId, String voiceId, String vehicleNumber,
                                User user, Device device, Event event, RobocallLog existingLog) {
        try {
            // Parse the JSON response
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            RobocallLog robocallLog;
            if (existingLog != null) {
                robocallLog = existingLog;
            } else {
                robocallLog = new RobocallLog();
                robocallLog.setCreatedAt(new Date());
                robocallLog.setRetryCount(0);
            }
            
            // Extract values from response
            String rcId = responseJson.has("rc_id") ? responseJson.get("rc_id").asText() : null;
            
            robocallLog.setRcId(rcId);
            robocallLog.setCallTo(callerId);
            robocallLog.setVoiceId(voiceId);
            robocallLog.setVehicleNumber(vehicleNumber);
            robocallLog.setCallStatus("initiated"); // Initial status
            robocallLog.setDeviceId(device.getId());
            robocallLog.setEventId(event.getId());
            robocallLog.setUserId(user.getId());
            robocallLog.setUpdatedAt(new Date());

            if (existingLog != null) {
                // Update existing log
                storage.updateObject(robocallLog, new Request(
                    new Columns.Exclude("id", "createdAt"),
                    new Condition.Equals("id", robocallLog.getId())));
                LOGGER.info("Updated robocall log for retry: rc_id={}", rcId);
            } else {
                // Create new log
                robocallLog.setId(storage.addObject(robocallLog, new Request(new Columns.Exclude("id"))));
                LOGGER.info("Saved robocall log: rc_id={}", rcId);
            }

        } catch (Exception e) {
            LOGGER.error("Error saving robocall log", e);
        }
    }

    private void saveFailedRobocallLog(String callerId, String voiceId, String vehicleNumber,
                                     User user, Device device, Event event, String errorMessage, RobocallLog existingLog) {
        try {
            RobocallLog robocallLog;
            if (existingLog != null) {
                robocallLog = existingLog;
            } else {
                robocallLog = new RobocallLog();
                robocallLog.setCreatedAt(new Date());
                robocallLog.setRetryCount(0);
            }

            robocallLog.setCallTo(callerId);
            robocallLog.setVoiceId(voiceId);
            robocallLog.setVehicleNumber(vehicleNumber);
            robocallLog.setCallStatus("failed");
            robocallLog.setDtmf("Error: " + errorMessage);
            robocallLog.setDeviceId(device.getId());
            robocallLog.setEventId(event.getId());
            robocallLog.setUserId(user.getId());
            robocallLog.setUpdatedAt(new Date());

            if (existingLog != null) {
                // Update existing log
                storage.updateObject(robocallLog, new Request(
                    new Columns.Exclude("id", "createdAt"),
                    new Condition.Equals("id", robocallLog.getId())));
                LOGGER.info("Updated failed robocall log for retry");
            } else {
                // Create new log
                robocallLog.setId(storage.addObject(robocallLog, new Request(new Columns.Exclude("id"))));
                LOGGER.info("Saved failed robocall log");
            }

            // Schedule retry for failed calls
            if (robocallLog.getRcId() != null) {
                retryManager.scheduleRetry(robocallLog.getRcId());
            }

        } catch (Exception e) {
            LOGGER.error("Error saving failed robocall log", e);
        }
    }

    private String getCallerId(User user) {
        String phone = user.getPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            // Clean and format phone number
            phone = phone.replaceAll("[^0-9+]", "");
            // Ensure it starts with country code format (e.g., +92 or 92)
            if (!phone.startsWith("+") && !phone.startsWith("92")) {
                phone = "92" + phone; // Default to Pakistan country code
            }
            return phone;
        }
        return null;
    }

    private Device getDevice(long deviceId) {
        try {
            return storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("id", deviceId)));
        } catch (StorageException e) {
            LOGGER.error("Failed to get device: {}", deviceId, e);
            return null;
        }
    }

    private String getVoiceId(Event event, Device device, User user) {
        // Priority order: specific event type mapping > event attributes > device attributes > user attributes > default
        String voiceId = null;

        // First check for event type specific voice ID mapping
        if (event.getType() != null) {
            voiceId = getVoiceIdForEventType(event.getType(), device, user);
        }

        // Check event attributes (for custom overrides)
        if (voiceId == null && event.getAttributes() != null) {
            voiceId = (String) event.getAttributes().get("robocallVoiceId");
        }

        // Check device attributes
        if (voiceId == null && device.getAttributes() != null) {
            voiceId = (String) device.getAttributes().get("robocallVoiceId");
        }

        // Check user attributes
        if (voiceId == null && user.getAttributes() != null) {
            voiceId = (String) user.getAttributes().get("robocallVoiceId");
        }

        // Default voice ID
        if (voiceId == null) {
            voiceId = "210"; // Default voice ID
        }

        return voiceId;
    }

    private String getVoiceIdForEventType(String eventType, Device device, User user) {
        // Check device-specific event type voice mapping first
        if (device.getAttributes() != null) {
            String deviceVoiceId = (String) device.getAttributes().get("robocallVoiceId." + eventType);
            if (deviceVoiceId != null) {
                return deviceVoiceId;
            }
        }

        // Check user-specific event type voice mapping
        if (user.getAttributes() != null) {
            String userVoiceId = (String) user.getAttributes().get("robocallVoiceId." + eventType);
            if (userVoiceId != null) {
                return userVoiceId;
            }
        }

        // Default voice IDs for common event types
        switch (eventType) {
            case "deviceOffline":
                return "211"; // Voice for device offline alerts
            case "deviceOnline":
                return "212"; // Voice for device online alerts
            case "deviceOverspeed":
                return "213"; // Voice for overspeed alerts
            case "geofenceEnter":
                return "214"; // Voice for geofence entry
            case "geofenceExit":
                return "215"; // Voice for geofence exit
            case "alarm":
                return "216"; // Voice for alarm events
            case "ignitionOn":
                return "217"; // Voice for ignition on
            case "ignitionOff":
                return "218"; // Voice for ignition off
            case "maintenance":
                return "219"; // Voice for maintenance alerts
            case "textMessage":
                return "220"; // Voice for text messages
            case "driverChanged":
                return "221"; // Voice for driver changes
            case "deviceMoving":
                return "222"; // Voice for device moving
            case "deviceStopped":
                return "223"; // Voice for device stopped
            case "deviceInactive":
                return "224"; // Voice for device inactive
            case "fuelDrop":
                return "225"; // Voice for fuel drop
            case "powerCut":
                return "226"; // Voice for power cut
            case "powerRestored":
                return "227"; // Voice for power restored
            default:
                return null; // No specific voice ID, will use fallback
        }
    }

    private String getVehicleNumber(Device device) {
        String vehicleNumber = null;

        // Try to get from device attributes first
        if (device.getAttributes() != null) {
            vehicleNumber = (String) device.getAttributes().get("numberPlate");
            if (vehicleNumber == null) {
                vehicleNumber = (String) device.getAttributes().get("vehicleNumber");
            }
        }

        // Fallback to device name
        if (vehicleNumber == null) {
            vehicleNumber = device.getName();
        }

        return vehicleNumber != null ? vehicleNumber : "Unknown";
    }

    private String getExpiryDate(Device device, Event event) {
        String expiryDate = null;

        // Try to get expiry date from device attributes
        if (device.getAttributes() != null) {
            Object expiry = device.getAttributes().get("expiryDate");
            if (expiry instanceof Date) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd of MMMM");
                expiryDate = sdf.format((Date) expiry);
            } else if (expiry instanceof String) {
                expiryDate = (String) expiry;
            }
        }

        // Fallback to event date
        if (expiryDate == null && event.getEventTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd of MMMM");
            expiryDate = sdf.format(event.getEventTime());
        }

        return expiryDate != null ? expiryDate : "Not specified";
    }

    private String buildApiUrl(String callerId, String voiceId, String text1, String text2)
            throws UnsupportedEncodingException {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?api_key=").append(URLEncoder.encode(apiKey, "UTF-8"));
        url.append("&caller_id=").append(URLEncoder.encode(callerId, "UTF-8"));
        url.append("&voice_id=").append(URLEncoder.encode(voiceId, "UTF-8"));
        url.append("&text1=").append(URLEncoder.encode(text1, "UTF-8"));
        url.append("&text2=").append(URLEncoder.encode(text2, "UTF-8"));

        return url.toString();
    }
}

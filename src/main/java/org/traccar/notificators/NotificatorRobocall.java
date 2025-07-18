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
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

@Singleton
public class NotificatorRobocall extends Notificator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificatorRobocall.class);

    private final Client client;
    private final Storage storage;
    private final String apiKey;
    private final String baseUrl;

    @Inject
    public NotificatorRobocall(
            Config config,
            NotificationFormatter notificationFormatter,
            Client client,
            Storage storage) {
        super(notificationFormatter, "short");
        this.client = client;
        this.storage = storage;
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

            // Extract voice ID from notification attributes
            String voiceId = getVoiceId(event, device, user);

            // Extract text1 (vehicle number/name)
            String text1 = getVehicleNumber(device);

            // Extract text2 (expiry date or other relevant info)
            String text2 = getExpiryDate(device, event);

            // Build the API URL
            String apiUrl = buildApiUrl(callerId, voiceId, text1, text2);

            LOGGER.info("Making robocall API request: {}", apiUrl);

            // Make the HTTP GET request
            try (Response response = client.target(apiUrl).request().get()) {
                if (response.getStatus() >= 200 && response.getStatus() < 300) {
                    LOGGER.info("Robocall API request successful for user: {}, device: {}",
                               user.getName(), device.getName());
                } else {
                    String responseBody = response.readEntity(String.class);
                    LOGGER.error("Robocall API request failed with status: {}, body: {}",
                                response.getStatus(), responseBody);
                    throw new MessageException("Robocall API request failed: " + response.getStatus());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error sending robocall notification", e);
            throw new MessageException("Failed to send robocall: " + e.getMessage());
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
        // Priority order: event attributes > device attributes > user attributes > default
        String voiceId = null;

        // Check event attributes first (if event has custom voice ID)
        if (event.getAttributes() != null) {
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
            voiceId = "210"; // Default voice ID as mentioned in requirements
        }

        return voiceId;
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

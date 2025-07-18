package org.traccar.notificators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Notification;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class NotificatorRobocallTest {

    @Mock
    private Config config;
    
    @Mock
    private NotificationFormatter notificationFormatter;
    
    @Mock
    private Client client;
    
    @Mock
    private Storage storage;
    
    @Mock
    private WebTarget webTarget;
    
    @Mock
    private Invocation.Builder builder;
    
    @Mock
    private Response response;

    private NotificatorRobocall notificator;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        when(config.getString(Keys.NOTIFICATOR_ROBOCALL_API_KEY)).thenReturn("test-api-key");
        when(config.getString(eq(Keys.NOTIFICATOR_ROBOCALL_URL), anyString()))
                .thenReturn("https://portal.robocall.pk/api/calls");
        
        notificator = new NotificatorRobocall(config, notificationFormatter, client, storage);
    }

    @Test
    public void testSuccessfulRobocall() throws Exception {
        // Setup user
        User user = new User();
        user.setPhone("923001234567");
        user.setName("Test User");
        
        // Setup device
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        Map<String, Object> deviceAttributes = new HashMap<>();
        deviceAttributes.put("numberPlate", "LEV6485");
        deviceAttributes.put("expiryDate", "10th of May");
        device.setAttributes(deviceAttributes);
        
        // Setup event
        Event event = new Event();
        event.setDeviceId(1L);
        event.setType("deviceOffline");
        
        // Setup position
        Position position = new Position();
        
        // Setup notification with voice ID
        Notification notification = new Notification();
        notification.setType("deviceOffline");
        notification.setVoiceId("210");
        
        // Setup message
        NotificationMessage message = new NotificationMessage("Test Subject", "Test Body");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        
        // Execute
        notificator.send(notification, user, event, position);
        
        // Verify
        verify(client).target(contains("api_key=test-api-key"));
        verify(client).target(contains("caller_id=923001234567"));
        verify(client).target(contains("text1=LEV6485"));
        verify(client).target(contains("text2=10th+of+May"));
        verify(client).target(contains("voice_id=210"));
    }

    @Test
    public void testPhoneNumberFormatting() throws Exception {
        // Setup user with different phone formats
        User user = new User();
        user.setPhone("+92-300-123-4567");
        user.setName("Test User");
        
        // Setup device
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        
        // Setup event
        Event event = new Event();
        event.setDeviceId(1L);
        
        // Setup position and message
        Position position = new Position();
        
        // Setup notification
        Notification notification = new Notification();
        notification.setType("deviceOffline");
        notification.setVoiceId("210");
        
        NotificationMessage message = new NotificationMessage("Test", "Test");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        
        // Execute
        notificator.send(notification, user, event, position);
        
        // Verify phone number is properly formatted
        verify(client).target(contains("caller_id=%2B923001234567"));
    }
}
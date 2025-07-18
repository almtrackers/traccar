package org.traccar.notificators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.RobocallRetryManager;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.RobocallLog;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
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
    private RobocallRetryManager retryManager;
    
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
        
        notificator = new NotificatorRobocall(config, notificationFormatter, client, storage, retryManager);
    }

    @Test
    public void testSuccessfulRobocall() throws Exception {
        // Setup user
        User user = new User();
        user.setId(1L);
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
        event.setId(100L);
        event.setDeviceId(1L);
        event.setType("deviceOffline");
        
        // Setup position
        Position position = new Position();
        
        // Setup message
        NotificationMessage message = new NotificationMessage("Test Subject", "Test Body");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.addObject(any(RobocallLog.class), any(Request.class))).thenReturn(1L);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn("{\"rc_id\":\"12345\",\"status\":\"success\"}");
        
        // Execute
        notificator.send(user, message, event, position);
        
        // Verify API call
        verify(client).target(contains("api_key=test-api-key"));
        verify(client).target(contains("caller_id=923001234567"));
        verify(client).target(contains("text1=LEV6485"));
        verify(client).target(contains("text2=10th+of+May"));
        verify(client).target(contains("voice_id=210"));
        
        // Verify database save
        verify(storage).addObject(any(RobocallLog.class), any(Request.class));
    }

    @Test
    public void testFailedRobocallWithRetry() throws Exception {
        // Setup user
        User user = new User();
        user.setId(1L);
        user.setPhone("923001234567");
        user.setName("Test User");
        
        // Setup device
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        
        // Setup event
        Event event = new Event();
        event.setId(100L);
        event.setDeviceId(1L);
        event.setType("deviceOffline");
        
        // Setup position and message
        Position position = new Position();
        NotificationMessage message = new NotificationMessage("Test", "Test");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.addObject(any(RobocallLog.class), any(Request.class))).thenReturn(1L);
        
        // Mock HTTP client for failure
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(500);
        when(response.readEntity(String.class)).thenReturn("Internal Server Error");
        
        // Execute and expect exception
        try {
            notificator.send(user, message, event, position);
        } catch (Exception e) {
            // Expected exception for failed API call
        }
        
        // Verify failed robocall was logged
        verify(storage).addObject(any(RobocallLog.class), any(Request.class));
    }

    @Test
    public void testPhoneNumberFormatting() throws Exception {
        // Setup user with different phone formats
        User user = new User();
        user.setId(1L);
        user.setPhone("+92-300-123-4567");
        user.setName("Test User");
        
        // Setup device
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        
        // Setup event
        Event event = new Event();
        event.setId(100L);
        event.setDeviceId(1L);
        
        // Setup position and message
        Position position = new Position();
        NotificationMessage message = new NotificationMessage("Test", "Test");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.addObject(any(RobocallLog.class), any(Request.class))).thenReturn(1L);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn("{\"rc_id\":\"12345\"}");
        
        // Execute
        notificator.send(user, message, event, position);
        
        // Verify phone number is properly formatted
        verify(client).target(contains("caller_id=%2B923001234567"));
    }

    @Test
    public void testEventTypeBasedVoiceId() throws Exception {
        // Setup user
        User user = new User();
        user.setId(1L);
        user.setPhone("923001234567");
        user.setName("Test User");
        
        // Setup device
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        
        // Setup event with specific type
        Event event = new Event();
        event.setId(100L);
        event.setDeviceId(1L);
        event.setType("deviceOffline"); // This should map to voice ID 211
        
        // Setup position and message
        Position position = new Position();
        NotificationMessage message = new NotificationMessage("Test", "Test");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.addObject(any(RobocallLog.class), any(Request.class))).thenReturn(1L);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn("{\"rc_id\":\"12345\"}");
        
        // Execute
        notificator.send(user, message, event, position);
        
        // Verify that voice_id=211 is used for deviceOffline events
        verify(client).target(contains("voice_id=211"));
    }

    @Test
    public void testCustomEventVoiceIdMapping() throws Exception {
        // Setup user
        User user = new User();
        user.setId(1L);
        user.setPhone("923001234567");
        user.setName("Test User");
        
        // Setup device with custom voice ID mapping for specific event type
        Device device = new Device();
        device.setId(1L);
        device.setName("Test Vehicle");
        Map<String, Object> deviceAttributes = new HashMap<>();
        deviceAttributes.put("robocallVoiceId.deviceOffline", "999"); // Custom voice for device offline
        device.setAttributes(deviceAttributes);
        
        // Setup event
        Event event = new Event();
        event.setId(100L);
        event.setDeviceId(1L);
        event.setType("deviceOffline");
        
        // Setup position and message
        Position position = new Position();
        NotificationMessage message = new NotificationMessage("Test", "Test");
        
        // Mock storage
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.addObject(any(RobocallLog.class), any(Request.class))).thenReturn(1L);
        
        // Mock HTTP client
        when(client.target(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.get()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn("{\"rc_id\":\"12345\"}");
        
        // Execute
        notificator.send(user, message, event, position);
        
        // Verify that custom voice_id=999 is used instead of default 211
        verify(client).target(contains("voice_id=999"));
    }
}
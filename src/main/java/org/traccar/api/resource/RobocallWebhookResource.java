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
package org.traccar.api.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.RobocallLog;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Date;

@Path("webhook/robocall")
public class RobocallWebhookResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobocallWebhookResource.class);

    private final Storage storage;

    @Inject
    public RobocallWebhookResource(Storage storage) {
        this.storage = storage;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response handleWebhook(
            @FormParam("rc_id") String rcId,
            @FormParam("call_status") String callStatus,
            @FormParam("dtmf") String dtmf,
            @FormParam("duration") String duration) {
        
        try {
            LOGGER.info("Received robocall webhook: rc_id={}, status={}, dtmf={}, duration={}", 
                       rcId, callStatus, dtmf, duration);

            if (rcId == null || rcId.trim().isEmpty()) {
                LOGGER.warn("Webhook received without rc_id");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("rc_id is required")
                    .build();
            }

            // Find the robocall log entry by rc_id
            RobocallLog robocallLog = storage.getObject(RobocallLog.class, new Request(
                new Columns.All(), new Condition.Equals("rcId", rcId)));

            if (robocallLog == null) {
                LOGGER.warn("No robocall log found for rc_id: {}", rcId);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("No robocall log found for rc_id: " + rcId)
                    .build();
            }

            // Update the robocall log with webhook data
            robocallLog.setCallStatus(callStatus);
            robocallLog.setDtmf(dtmf);
            
            if (duration != null && !duration.trim().isEmpty()) {
                try {
                    robocallLog.setDuration(Integer.parseInt(duration));
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid duration format: {}", duration);
                }
            }
            
            robocallLog.setUpdatedAt(new Date());

            // Save the updated robocall log
            storage.updateObject(robocallLog, new Request(
                new Columns.Exclude("id", "createdAt", "rcId", "callTo", "voiceId", 
                                   "vehicleNumber", "deviceId", "eventId", "userId", "retryCount", "nextRetryAt"),
                new Condition.Equals("id", robocallLog.getId())));

            LOGGER.info("Successfully updated robocall log for rc_id: {}", rcId);
            
            return Response.ok("Webhook processed successfully").build();

        } catch (StorageException e) {
            LOGGER.error("Database error while processing robocall webhook", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Database error: " + e.getMessage())
                .build();
        } catch (Exception e) {
            LOGGER.error("Unexpected error while processing robocall webhook", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Internal server error: " + e.getMessage())
                .build();
        }
    }
}
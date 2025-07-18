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
package org.traccar.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.RobocallLog;
import org.traccar.model.User;
import org.traccar.notificators.NotificatorRobocall;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class RobocallRetryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobocallRetryManager.class);
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_INTERVAL_MINUTES = 5;

    private final Storage storage;
    private final NotificatorRobocall notificatorRobocall;
    private final ScheduledExecutorService scheduler;

    @Inject
    public RobocallRetryManager(Config config, Storage storage, NotificatorRobocall notificatorRobocall) {
        this.storage = storage;
        this.notificatorRobocall = notificatorRobocall;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Start the retry processor
        startRetryProcessor();
    }

    private void startRetryProcessor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                processRetries();
            } catch (Exception e) {
                LOGGER.error("Error processing robocall retries", e);
            }
        }, 1, 1, TimeUnit.MINUTES); // Check every minute
    }

    public void scheduleRetry(String rcId) {
        try {
            // Find the robocall log entry
            RobocallLog robocallLog = storage.getObject(RobocallLog.class, new Request(
                new Columns.All(), new Condition.Equals("rcId", rcId)));

            if (robocallLog == null) {
                LOGGER.warn("Cannot schedule retry - robocall log not found for rc_id: {}", rcId);
                return;
            }

            // Check if we've reached max retry attempts
            if (robocallLog.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                LOGGER.info("Max retry attempts reached for rc_id: {}", rcId);
                return;
            }

            // Schedule next retry
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, RETRY_INTERVAL_MINUTES);
            
            robocallLog.setNextRetryAt(calendar.getTime());
            robocallLog.setRetryCount(robocallLog.getRetryCount() + 1);
            robocallLog.setUpdatedAt(new Date());

            storage.updateObject(robocallLog, new Request(
                new Columns.Include("nextRetryAt", "retryCount", "updatedAt"),
                new Condition.Equals("id", robocallLog.getId())));

            LOGGER.info("Scheduled retry {} for rc_id: {} at {}", 
                       robocallLog.getRetryCount(), rcId, calendar.getTime());

        } catch (StorageException e) {
            LOGGER.error("Error scheduling robocall retry for rc_id: {}", rcId, e);
        }
    }

    private void processRetries() {
        try {
            Date now = new Date();
            
            // Find robocall logs that need retry and are not answered
            Collection<RobocallLog> retryLogs = storage.getObjects(RobocallLog.class, new Request(
                new Columns.All(),
                new Condition.And(
                    new Condition.Compare("nextRetryAt", "<=", "nextRetryAt", now),
                    new Condition.And(
                        new Condition.Compare("retryCount", "<", "retryCount", MAX_RETRY_ATTEMPTS),
                        new Condition.Or(
                            new Condition.Equals("callStatus", null),
                            new Condition.Or(
                                new Condition.Equals("callStatus", ""),
                                new Condition.Or(
                                    new Condition.Equals("callStatus", "failed"),
                                    new Condition.Or(
                                        new Condition.Equals("callStatus", "no-answer"),
                                        new Condition.Equals("callStatus", "busy")
                                    )
                                )
                            )
                        )
                    )
                )
            ));

            for (RobocallLog robocallLog : retryLogs) {
                try {
                    LOGGER.info("Processing retry for rc_id: {}, attempt: {}", 
                               robocallLog.getRcId(), robocallLog.getRetryCount());

                    // Clear next retry time to prevent multiple retries
                    robocallLog.setNextRetryAt(null);
                    storage.updateObject(robocallLog, new Request(
                        new Columns.Include("nextRetryAt"),
                        new Condition.Equals("id", robocallLog.getId())));

                    // Retry the robocall
                    retryRobocall(robocallLog);

                } catch (Exception e) {
                    LOGGER.error("Error retrying robocall for rc_id: {}", robocallLog.getRcId(), e);
                }
            }

        } catch (StorageException e) {
            LOGGER.error("Error fetching robocall logs for retry", e);
        }
    }

    private void retryRobocall(RobocallLog robocallLog) throws StorageException {
        // Get the related objects for retry
        Device device = storage.getObject(Device.class, new Request(
            new Columns.All(), new Condition.Equals("id", robocallLog.getDeviceId())));

        Event event = storage.getObject(Event.class, new Request(
            new Columns.All(), new Condition.Equals("id", robocallLog.getEventId())));

        User user = storage.getObject(User.class, new Request(
            new Columns.All(), new Condition.Equals("id", robocallLog.getUserId())));

        Position position = null;
        if (event != null && event.getPositionId() > 0) {
            position = storage.getObject(Position.class, new Request(
                new Columns.All(), new Condition.Equals("id", event.getPositionId())));
        }

        if (device == null || event == null || user == null) {
            LOGGER.warn("Cannot retry robocall - missing required objects for rc_id: {}", robocallLog.getRcId());
            return;
        }

        try {
            // Attempt to make the robocall again
            notificatorRobocall.retryCall(user, device, event, position, robocallLog);
            
        } catch (Exception e) {
            LOGGER.error("Failed to retry robocall for rc_id: {}", robocallLog.getRcId(), e);
            
            // Schedule another retry if we haven't reached max attempts
            if (robocallLog.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                scheduleRetry(robocallLog.getRcId());
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
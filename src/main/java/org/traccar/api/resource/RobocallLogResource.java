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

import org.traccar.api.BaseResource;
import org.traccar.model.Device;
import org.traccar.model.RobocallLog;
import org.traccar.model.User;
import org.traccar.model.UserRestrictions;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;

@Path("robocall/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RobocallLogResource extends BaseResource {

    @GET
    public Collection<RobocallLog> get(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("userId") long userId,
            @QueryParam("limit") int limit) throws StorageException {

        permissionsService.checkRestriction(getUserId(), UserRestrictions::getReadonly);

        Condition conditions = new Condition.Permission(User.class, getUserId(), RobocallLog.class);

        if (deviceId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions = new Condition.And(
                conditions,
                new Condition.Equals("deviceId", deviceId)
            );
        }

        if (userId > 0) {
            permissionsService.checkPermission(User.class, getUserId(), userId);
            conditions = new Condition.And(
                conditions,
                new Condition.Equals("userId", userId)
            );
        }

        var request = new Request(
            new Columns.All(),
            conditions,
            new Order("createdAt", true, limit > 0 ? limit : 100)
        );

        return storage.getObjects(RobocallLog.class, request);
    }
}
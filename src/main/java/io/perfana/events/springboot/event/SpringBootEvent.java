/*
 * Copyright (C) 2020-2022 Peter Paul Bakker - Perfana
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
package io.perfana.events.springboot.event;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.perfana.events.springboot.actuator.ActuatorClient;
import io.perfana.events.springboot.actuator.Variable;
import io.perfana.eventscheduler.api.CustomEvent;
import io.perfana.eventscheduler.api.EventAdapter;
import io.perfana.eventscheduler.api.EventLogger;
import io.perfana.eventscheduler.api.message.EventMessage;
import io.perfana.eventscheduler.api.message.EventMessageBus;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.perfana.events.springboot.event.SpringBootEvent.AllowedCustomEvents.*;

public class SpringBootEvent extends EventAdapter<SpringBootEventContext> {

    private ActuatorClient actuatorClient;

    private final Gson gson = new Gson();

    enum AllowedCustomEvents {
        heapdump("heapdump"), stackdump("stackdump");

        private final String eventName;

        AllowedCustomEvents(String eventName) {
            this.eventName = eventName;
        }

        public String getEventName() {
            return eventName;
        }

        public static Stream<AllowedCustomEvents> stream() {
            return Stream.of(values());
        }
        
        public boolean hasEventName(String name) {
            return this.eventName.equals(name);
        }
    }

    private final Set<String> allowedCustomEvents = setOf(AllowedCustomEvents.stream()
        .map(AllowedCustomEvents::getEventName)
        .toArray(String[]::new));

    public SpringBootEvent(SpringBootEventContext eventContext, EventMessageBus messageBus, EventLogger logger) {
        super(eventContext, messageBus, logger);

        this.eventMessageBus.addReceiver(m -> logger.info("Received message: " + m));
    }

    @Override
    public Collection<String> allowedCustomEvents() {
        return allowedCustomEvents;
    }

    @Override
    public void beforeTest() {
        logger.info("Fetching actuator values for [" + eventContext.getTestContext().getTestRunId() + "]");

        String pluginName = SpringBootEvent.class.getSimpleName() + "-" + eventContext.getName();

        String actuatorBaseUrl = eventContext.getActuatorBaseUrl();

        List<Variable> variables = new ArrayList<>();
        if (actuatorBaseUrl != null) {
            actuatorClient = new ActuatorClient(actuatorBaseUrl, logger);
            variables.addAll(actuatorClient.queryActuator(eventContext.getActuatorEnvProperties()));
            String info = actuatorClient.info();
            logger.info("Application info: " + info);

            if (info.contains("version")) {
                try {
                    Type type = new TypeToken<Map<String, Object>>() {
                    }.getType();
                    Map<String, Object> infoMap = gson.fromJson(info, type);
                    Map<String, Object> build = (Map<String, Object>) infoMap.get("build");
                    String version = (String) build.get("version");
                    variables.add(new Variable("version", version));
                } catch (Exception e) {
                    logger.warn("cannot retrieve version from info object: " + info);
                }
            }
        }

        EventMessage.EventMessageBuilder builder = EventMessage.builder();

        String actuatorPropPrefix = eventContext.getActuatorPropPrefix();

        variables.forEach(v -> builder.variable(actuatorPropPrefix + "-" + v.getName(), v.getValue()));

        this.eventMessageBus.send(builder.pluginName(pluginName).build());

        this.eventMessageBus.send(EventMessage.builder().pluginName(pluginName).message("Go!").build());
    }

    @Override
    public void customEvent(CustomEvent scheduleEvent) {

        String eventName = scheduleEvent.getName();
        
        if (stackdump.hasEventName(eventName)) {
            stackdump(scheduleEvent);
        }
        else if (heapdump.hasEventName(eventName)) {
            heapdumpEvent(scheduleEvent);
        }
        else {
            logger.info("WARNING: ignoring unknown event [" + eventName + "]");
        }
    }

    private void heapdumpEvent(CustomEvent scheduleEvent) {
        logger.info("Start " + scheduleEvent);
        String testRunId = eventContext.getTestContext().getTestRunId();
        logger.info("Heap dump for " + testRunId);
        actuatorClient.heapdump(testRunId);
    }

    private void stackdump(CustomEvent scheduleEvent) {
        Map<String, String> parsedSettings = parseSettings(scheduleEvent.getSettings());
        String testRunId = eventContext.getTestContext().getTestRunId();
        logger.info("stack dump event for test [" + testRunId + "] with parsed settings: " + parsedSettings);
        actuatorClient.threaddump(testRunId);
    }

    static Map<String, String> parseSettings(String eventSettings) {
        if (eventSettings == null || eventSettings.trim().length() == 0) {
            return Collections.emptyMap();
        }
        return Arrays.stream(eventSettings.split(";"))
                .map(s -> s.split("="))
                .collect(Collectors.toMap(k -> k[0], v -> v.length == 2 ? v[1] : ""));
    }
}

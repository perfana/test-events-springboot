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
import io.perfana.eventscheduler.exception.EventSchedulerRuntimeException;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.perfana.events.springboot.event.SpringBootEvent.AllowedCustomEvents.*;

public class SpringBootEvent extends EventAdapter<SpringBootEventContext> {

    public static final String ACTUATOR_TAG = "actuator";
    private ActuatorClient actuatorClient;

    private final Gson gson = new Gson();

    enum AllowedCustomEvents {
        heapdump("heapdump"), threaddump("threaddump");

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

        this.eventMessageBus.addReceiver(m -> logger.debug("Received message: " + m));
    }

    @Override
    public Collection<String> allowedCustomEvents() {
        return allowedCustomEvents;
    }

    @Override
    public void beforeTest() {
        logger.info("Fetching actuator values for [" + eventContext.getTestContext().getTestRunId() + "]");

        String pluginName = SpringBootEvent.class.getSimpleName() + "-" + eventContext.getName();
        String tags = filterAndCombineTagsForTestRunConfigCall();

        Map<String, String> configLines = createTestRunConfigLines();
        configLines.forEach((name, value) -> sendKeyValueMessage(name, value, pluginName, tags));

        List<Variable> variables = getActuatorVariables();
        variables.forEach(v -> sendKeyValueMessage(v.getName(), v.getValue(), pluginName, tags));

        this.eventMessageBus.send(EventMessage.builder().pluginName(pluginName).message("Go!").build());
    }

    private Map<String, String> createTestRunConfigLines() {
        String prefix = "event." + eventContext.getName() + ".";
        Map<String, String> lines = new HashMap<>();
        lines.put(prefix + "dumpPath", eventContext.getDumpPath());
        lines.put(prefix + "actuatorEnvProperties", String.valueOf(eventContext.getActuatorEnvProperties()));
        lines.put(prefix + "actuatorBaseUrl", eventContext.getActuatorBaseUrl());
        return lines;
    }

    private List<Variable> getActuatorVariables() {
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
        return variables;
    }

    /**
     * Gets tags from event context and adds "actuator" tag if not present,
     * and adds actuatorPropPrefix if not part of tags.
     *
     * @return comma separated list of tags
     */
    private String filterAndCombineTagsForTestRunConfigCall() {
        String actuatorPropPrefix = eventContext.getActuatorPropPrefix();
        String tags = eventContext.getTags();

        List<String> tagsAsList = new ArrayList<>(Arrays.asList(tags.split(",")));
        // this can go as actuatorPropPrefix is removed!
        if (!tagsAsList.contains(actuatorPropPrefix)) {
            tagsAsList.add(actuatorPropPrefix);
        }
        if (!tagsAsList.contains(ACTUATOR_TAG)) {
            tagsAsList.add(ACTUATOR_TAG);
        }

        return String.join(",", tagsAsList);
    }

    private void sendKeyValueMessage(String key, String value, String pluginName, String tags) {

        EventMessage.EventMessageBuilder messageBuilder = EventMessage.builder();

        messageBuilder.variable("message-type", "test-run-config");
        messageBuilder.variable("output", "key");
        messageBuilder.variable("tags", tags);

        messageBuilder.variable("key", key);
        messageBuilder.message(value);

        this.eventMessageBus.send(messageBuilder.pluginName(pluginName).build());
    }

    @Override
    public void customEvent(CustomEvent scheduleEvent) {
        String eventName = scheduleEvent.getName();
        try {
            if (threaddump.hasEventName(eventName)) {
                threadDumpEvent(scheduleEvent);
            } else if (heapdump.hasEventName(eventName)) {
                heapDumpEvent(scheduleEvent);
            } else {
                logger.warn("ignoring unknown event [" + eventName + "]");
            }
        } catch (Exception e) {
            logger.error("Failed to run custom event: " + eventName, e);
        }
    }

    private void heapDumpEvent(CustomEvent scheduleEvent) {
        logger.info("Start " + scheduleEvent);
        File dumpPath = sanityPath(eventContext.getDumpPath());
        String filename = uniqueFileNameFromTags();
        logger.info("Heap dump for " + filename);
        actuatorClient.heapdump(dumpPath, filename);
    }

    private String uniqueFileNameFromTags() {
        String testRunId = eventContext.getTestContext().getTestRunId();
        String tags = eventContext.getTags();
        if (tags.isEmpty()) {
            return testRunId;
        } else {
            String tagsForName = tags.replace(',', '-');
            return sanitizeFilename(testRunId + "-" + tagsForName);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[:\\\\/*?|<>]", "_");
    }

    private void threadDumpEvent(CustomEvent scheduleEvent) {
        logger.info("Start " + scheduleEvent);
        File dumpPath = sanityPath(eventContext.getDumpPath());
        String filename = uniqueFileNameFromTags();
        logger.info("stack dump event for test [" + filename + "]");
        actuatorClient.threaddump(dumpPath, filename);
    }

    private File sanityPath(String dumpPath) {
        File dumpDir;
        if (dumpPath == null || dumpPath.trim().isEmpty()) {
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir == null || tmpDir.trim().isEmpty()) {
                throw new EventSchedulerRuntimeException("No java.io.tmpdir env found, better define explicit dumpPath in config.");
            }
            dumpDir = new File(tmpDir);
        }
        else {
            dumpDir = new File(dumpPath);
        }

        if (!dumpDir.exists()) {
            throw new EventSchedulerRuntimeException("Dir does not exist: " + dumpDir);
        }
        if (!dumpDir.isDirectory()) {
            throw new EventSchedulerRuntimeException("Dir is not a directory: " + dumpDir);
        }
        if (!dumpDir.canWrite()) {
            throw new EventSchedulerRuntimeException("Dir is not writeable: " + dumpDir);
        }
        return dumpDir;
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

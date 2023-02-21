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
import com.squareup.okhttp.OkHttpClient;
import io.perfana.events.springboot.actuator.ActuatorClient;
import io.perfana.events.springboot.actuator.OkHttpClientFactory;
import io.perfana.events.springboot.actuator.Variable;
import io.perfana.eventscheduler.api.CustomEvent;
import io.perfana.eventscheduler.api.EventAdapter;
import io.perfana.eventscheduler.api.EventLogger;
import io.perfana.eventscheduler.api.message.EventMessage;
import io.perfana.eventscheduler.api.message.EventMessageBus;
import io.perfana.eventscheduler.exception.EventSchedulerRuntimeException;
import io.perfana.eventscheduler.util.JavaArgsParser;
import io.perfana.eventscheduler.util.TestRunConfigUtil;

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

    private OkHttpClient okHttpClient = OkHttpClientFactory.instance();

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


    // for test only
    void injectOkHttpClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

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

        Map<String, String> keyValues = createTestRunKeyValues();

        List<Variable> variables = getActuatorVariables();
        variables.forEach(v -> keyValues.put(v.getName(), v.getValue()));

        EventMessage message = TestRunConfigUtil.createTestRunConfigMessageKeys(pluginName, keyValues, tags);
        eventMessageBus.send(message);

        eventMessageBus.send(EventMessage.builder().pluginName(pluginName).message("Go!").build());
    }

    private Map<String, String> createTestRunKeyValues() {
        String prefix = "event." + eventContext.getName() + ".";
        Map<String, String> keyValues = new HashMap<>();
        keyValues.put(prefix + "dumpPath", eventContext.getDumpPath());
        keyValues.put(prefix + "actuatorEnvProperties", String.join(TestRunConfigUtil.VALUE_LIST_DELIMITER, eventContext.getActuatorEnvProperties()));
        keyValues.put(prefix + "actuatorBaseUrl", eventContext.getActuatorBaseUrl());
        return keyValues;
    }

    private List<Variable> getActuatorVariables() {
        String actuatorBaseUrl = eventContext.getActuatorBaseUrl();

        List<Variable> variables = new ArrayList<>();
        if (actuatorBaseUrl != null) {
            actuatorClient = new ActuatorClient(actuatorBaseUrl, okHttpClient, logger);
            List<String> actuatorEnvProperties = eventContext.getActuatorEnvProperties();
            logger.debug("Requested actuatorEnvProperties: " + actuatorEnvProperties);
            List<Variable> actuatorKeyValues = actuatorClient.queryActuator(actuatorEnvProperties);
            logger.debug("Found actuator values: " + actuatorKeyValues);
            List<Variable> processedVariables = processJavaArgsLikeOptions(actuatorKeyValues);
            variables.addAll(processedVariables);
            String info = actuatorClient.info();
            logger.debug("Application info: " + info);

            if (info.contains("version")) {
                try {
                    Type type = new TypeToken<Map<String, Object>>() {}.getType();
                    Map<String, Object> infoMap = gson.fromJson(info, type);
                    Map<String, Object> build = (Map<String, Object>) infoMap.get("build");
                    String version = (String) build.get("version");
                    variables.add(new Variable("version", version));
                } catch (Exception e) {
                    logger.warn("cannot retrieve version from info object: " + info);
                }
            }
        }
        logger.debug("All processed actuator values: " + variables);
        return variables;
    }

     static List<Variable> processJavaArgsLikeOptions(List<Variable> actuatorKeyValues) {
        List<Variable> clonedVariables = new ArrayList<>(actuatorKeyValues);

        List<Variable> javaArgsVariables = actuatorKeyValues.stream()
                .filter(v -> JavaArgsParser.isJavaCommandArgsProperty(v.getName())).collect(Collectors.toList());
        clonedVariables.removeAll(javaArgsVariables);

        javaArgsVariables.forEach(v -> addAllJvmArgOptions(clonedVariables, v));

        return clonedVariables;
    }

    private static void addAllJvmArgOptions(List<Variable> clonedVariables, Variable v) {
        JavaArgsParser.createJvmArgsTestConfigLines(v.getValue())
                .forEach((k, v2) -> clonedVariables.add(new Variable(v.getName() + "." + k, v2)));
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

        List<String> splitList = new ArrayList<>(Arrays.asList(tags.split(",")));
        List<String> tagsAsListNoEmpties = splitList.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

        // this can go as actuatorPropPrefix is removed!
        if (!tagsAsListNoEmpties.contains(actuatorPropPrefix)) {
            tagsAsListNoEmpties.add(actuatorPropPrefix);
        }
        if (!tagsAsListNoEmpties.contains(ACTUATOR_TAG)) {
            tagsAsListNoEmpties.add(ACTUATOR_TAG);
        }

        return String.join(",", tagsAsListNoEmpties);
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

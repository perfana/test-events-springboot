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

import io.perfana.events.springboot.actuator.Variable;
import io.perfana.eventscheduler.EventMessageBusSimple;
import io.perfana.eventscheduler.api.CustomEvent;
import io.perfana.eventscheduler.api.config.TestConfig;
import io.perfana.eventscheduler.api.message.EventMessageBus;
import io.perfana.eventscheduler.log.EventLoggerStdOut;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.perfana.events.springboot.TestUtil.createOkHttpClientMock200;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootEventTest {

    @Test
    void beforeTest() throws IOException {
        SpringBootEventConfig eventConfig = new SpringBootEventConfig();
        eventConfig.setEventFactory(SpringBootEventFactory.class.getSimpleName());
        eventConfig.setName("myEvent1");
        eventConfig.setEnabled(true);
        eventConfig.setActuatorBaseUrl("http://localhost:8080/actuator");
        eventConfig.setDumpPath("/tmp");
        eventConfig.setTags("after/burner,beta");
        eventConfig.setActuatorEnvProperties("afterburner.remote.call.httpclient.connect.timeout.millis,afterburner.remote.call.httpclient.connections.max");

        EventMessageBus messageBus = new EventMessageBusSimple();

        SpringBootEventContext eventContext = eventConfig.toContext();
        SpringBootEvent event = new SpringBootEvent(eventContext, TestConfig.builder().build().toContext(), messageBus, EventLoggerStdOut.INSTANCE_DEBUG);

        event.injectOkHttpClient(createOkHttpClientMock200());

        event.beforeTest();
        event.keepAlive();
        event.customEvent(CustomEvent.createFromLine("PT3S|heapdump|debug=true"));
        event.customEvent(CustomEvent.createFromLine("PT1M|threaddump"));
        event.customEvent(CustomEvent.createFromLine("PT1H2M3S|threaddump|"));
        event.afterTest();

        // not much to assert really... just look at System.out and
        // check it does not blow with an Exception...
        assertEquals("myEvent1", eventContext.getName());
        assertEquals("after/burner,beta", eventContext.getTags());

    }

    @Test
    void beforeTestMinimal() throws IOException {
        SpringBootEventConfig eventConfig = new SpringBootEventConfig();
        eventConfig.setEventFactory(SpringBootEventFactory.class.getSimpleName());

        EventMessageBus messageBus = new EventMessageBusSimple();

        SpringBootEvent event = new SpringBootEvent(eventConfig.toContext(), TestConfig.builder().build().toContext(), messageBus, EventLoggerStdOut.INSTANCE);
        event.injectOkHttpClient(createOkHttpClientMock200());
        event.beforeTest();
    }

    @Test
    void parseSettingsZero() {
        Map<String, String> settings = SpringBootEvent.parseSettings("");
        assertEquals(0, settings.size());
    }

    @Test
    void parseSettingsOne() {
        Map<String, String> settings = SpringBootEvent.parseSettings("foo=bar");
        assertEquals(1, settings.size());
        assertEquals("bar", settings.get("foo"));
    }

    @Test
    void parseSettingsTwo() {
        Map<String, String> settings = SpringBootEvent.parseSettings("foo=bar;name=perfana");
        assertEquals(2, settings.size());
        assertEquals("bar", settings.get("foo"));
        assertEquals("perfana", settings.get("name"));
    }

    @Test
    void parseSettingsNoValue() {
        Map<String, String> settings = SpringBootEvent.parseSettings("foo=bar;name");
        assertEquals(2, settings.size());
        assertEquals("bar", settings.get("foo"));
        assertEquals("", settings.get("name"));
    }

    @Test
    void parseSettingsNoEntry() {
        Map<String, String> settings = SpringBootEvent.parseSettings("foo=bar;");
        assertEquals(1, settings.size());
        assertEquals("bar", settings.get("foo"));
    }

    @Test
    void parseJvmArgsVariables() {
        List<Variable> variables = new ArrayList<>();
        variables.add(new Variable("JAVA_OPTS", "-Xms1g -Xmx2g"));
        variables.add(new Variable("anything", "is-it-present"));

        List<Variable> processedVariables = SpringBootEvent.processJavaArgsLikeOptions(variables);

        assertEquals(3, processedVariables.size());
        assertTrue(processedVariables.stream().anyMatch(v -> v.getName().equals("JAVA_OPTS.jvmArg.Xms")), "variable not found");
        assertTrue(processedVariables.stream().anyMatch(v -> v.getName().equals("JAVA_OPTS.jvmArg.Xmx")), "variable not found");
        assertEquals("1g", processedVariables.stream().filter(v -> v.getName().equals("JAVA_OPTS.jvmArg.Xms")).findFirst().get().getValue());
        assertEquals("2g", processedVariables.stream().filter(v -> v.getName().equals("JAVA_OPTS.jvmArg.Xmx")).findFirst().get().getValue());
        assertEquals("is-it-present", processedVariables.stream().filter(v -> v.getName().equals("anything")).findFirst().get().getValue());
    }

}

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
package io.perfana.events.springboot.actuator;

import com.squareup.okhttp.*;
import io.perfana.events.springboot.TestUtil;
import io.perfana.eventscheduler.log.EventLoggerStdOut;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActuatorClientTest {

    @Test
    void testQuery() throws IOException {

        final OkHttpClient okHttpClient = TestUtil.createOkHttpClientMock200();

        ActuatorClient actuatorClient = new ActuatorClient("http://localhost:8080/actuator", okHttpClient, EventLoggerStdOut.INSTANCE);

        List<String> properties = new ArrayList<>();
        properties.add("java.runtime.version");
        properties.add("USER");
        properties.add("doesNotExist");

        List<Variable> variables = actuatorClient.queryActuator(properties);

        assertEquals(2, variables.size());
        assertEquals("systemProperties:java.runtime.version", variables.get(0).getName());
        assertEquals("17.0.3+7-LTS", variables.get(0).getValue());
        assertEquals("systemEnvironment:USER", variables.get(1).getName());
        assertEquals("pp", variables.get(1).getValue());

    }

    @Test
    void testRetryNon200() throws IOException {

        final OkHttpClient okHttpClient = TestUtil.createOkHttpClientMock503();

        ActuatorClient actuatorClient = new ActuatorClient("http://localhost:8080/actuator", okHttpClient, EventLoggerStdOut.INSTANCE);

        List<String> properties = new ArrayList<>();
        properties.add("java.runtime.version");
        properties.add("USER");
        properties.add("doesNotExist");

        assertTrue(actuatorClient.queryActuator(properties).isEmpty());

    }

    @Test
    void testRetryTimeout() throws IOException {

        final OkHttpClient okHttpClient = mock(OkHttpClient.class);
        final Call remoteCall = mock(Call.class);

        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        when(remoteCall.execute()).thenThrow(new IOException("Timeout!"));

        ActuatorClient actuatorClient = new ActuatorClient("http://localhost:8080/actuator", okHttpClient, EventLoggerStdOut.INSTANCE);

        List<String> properties = new ArrayList<>();
        properties.add("java.runtime.version");
        properties.add("USER");
        properties.add("doesNotExist");

        assertTrue(actuatorClient.queryActuator(properties).isEmpty());

    }

}

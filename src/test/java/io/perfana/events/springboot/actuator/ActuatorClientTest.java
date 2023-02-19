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
import io.perfana.eventscheduler.log.EventLoggerStdOut;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActuatorClientTest {

    @Test
    void testQuery() throws IOException {

        final OkHttpClient okHttpClient = mock(OkHttpClient.class);
        final Call remoteCall = mock(Call.class);

        final String serializedBody = loadFileFromTestResources("actuator.env.response.json");

        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), serializedBody);

        final Response response = new Response.Builder()
                .request(new Request.Builder().url("http://localhost:8080/actuator/env").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(body)
                .build();

        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

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

        final OkHttpClient okHttpClient = mock(OkHttpClient.class);
        final Call remoteCall = mock(Call.class);

        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), "");

        final Response response = new Response.Builder()
                .request(new Request.Builder().url("http://localhost:8080/actuator/env").build())
                .protocol(Protocol.HTTP_1_1)
                .code(503).message("Service not ready").body(body)
                .build();

        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

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

    private String loadFileFromTestResources(String resource) throws IOException {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(resource);
        if (resourceAsStream == null) {
            throw new IOException("Resource not found: " + resource);
        }
        return new String(resourceAsStream.readAllBytes());
    }

}

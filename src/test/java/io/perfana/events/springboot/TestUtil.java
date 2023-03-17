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
package io.perfana.events.springboot;

import com.squareup.okhttp.*;

import java.io.IOException;
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtil {
    public static OkHttpClient createOkHttpClientMock503() throws IOException {
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
        return okHttpClient;
    }

    public static OkHttpClient createOkHttpClientMock200() throws IOException {
        final OkHttpClient okHttpClient = mock(OkHttpClient.class);
        final Call remoteCall = mock(Call.class);

        final String serializedBody = loadFileFromTestResources("actuator.env.response.json");

        MediaType jsonMediaType = MediaType.parse("application/json");
        ResponseBody body = ResponseBody.create(jsonMediaType, serializedBody);

        final Response response = new Response.Builder()
                .request(new Request.Builder().url("http://localhost:8080/actuator/env").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).body(body)
                .build();

        final Response response2 = new Response.Builder()
                .request(new Request.Builder().url("http://localhost:8080/actuator/info").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).body(ResponseBody.create(jsonMediaType, "{\"build\":{\"artifact\":\"afterburner-java\",\"name\":\"afterburner-java\",\"time\":\"2023-02-21T08:23:20.867Z\",\"version\":\"2.2.0-SNAPSHOT\",\"group\":\"io.perfana\"}}"))
                .build();

        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        when(remoteCall.execute()).thenReturn(response).thenReturn(response2);

        return okHttpClient;
    }

    public static String loadFileFromTestResources(String resource) throws IOException {
        InputStream resourceAsStream = TestUtil.class.getClassLoader().getResourceAsStream(resource);
        if (resourceAsStream == null) {
            throw new IOException("Resource not found: " + resource);
        }
        return new String(resourceAsStream.readAllBytes());
    }
}

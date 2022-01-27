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

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;
import java.util.Map;

public class BinaryFileDownloader implements AutoCloseable {

    private final OkHttpClient client;
    private final BinaryFileWriter writer;

    public BinaryFileDownloader(OkHttpClient client, BinaryFileWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    public void download(String url, Map<String, String> headers) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        headers.forEach(requestBuilder::header);
        Request request = requestBuilder.build();

        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            throw new IllegalStateException("Response doesn't contain a file");
        }
        //double length = Double.parseDouble(Objects.requireNonNull(response.header("Content-Length", "1")));
        writer.write(responseBody.byteStream());
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}

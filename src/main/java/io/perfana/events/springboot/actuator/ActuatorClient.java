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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.perfana.eventscheduler.api.EventLogger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActuatorClient {

    private final String baseUrl;

    private final Gson gson = new GsonBuilder().create();

    private final OkHttpClient okHttpClient;

    private final EventLogger logger;

    private static final int retries = 2;
    private static final List<Integer> retryCodes = List.of(408, 425 , 429, 500, 502, 503, 504);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");

    public ActuatorClient(String actuatorUrl, OkHttpClient okHttpClient, EventLogger logger) {
        this.baseUrl = actuatorUrl;
        this.okHttpClient = okHttpClient;
        this.logger = logger;
    }

    public List<Variable> queryActuator(List<String> envKeys) {

        String totalUrl = baseUrl + "/env";
        try {
            String result = remoteCall(totalUrl);

            ActuatorEnvs envs = gson.fromJson(result, ActuatorEnvs.class);

            return envs.propertySources.stream()
                    .flatMap(propertySource -> filterAndPrefixProperties(propertySource.name, envKeys, propertySource.properties.entrySet()))
                    .collect(Collectors.toList());

        } catch (ActuatorClientException ex) {
            logger.error("Cannot get " + totalUrl, ex);
            return Collections.emptyList();
        }
    }


    public String info() {
        // http://localhost:8080/actuator/info
        String totalUrl = baseUrl + "/info";
        try {
            return remoteCall(totalUrl);
        } catch (ActuatorClientException ex) {
            logger.error("Cannot get " + totalUrl, ex);
            return "{}";
        }
    }

    private String remoteCall(String url) throws ActuatorClientException {
        int count = 0;
        while (true) {
            count++;
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                Response response = okHttpClient.newCall(request).execute();

                int code = response.code();
                if (code != 200) {
                    String message = "Unexpected status code (not 200): " + code + " for " + request.url() + ": " + response.message();
                    if (count <= retries && retryCodes.contains(code)) {
                        logger.warn("Retry (" + count + "/" + retries + ") for call: " + message);
                        waitForRetry();
                        continue;
                    }
                    else {
                        throw new ActuatorClientException(message);
                    }
                }
                return response.body().string();
            } catch (IOException e) {
                String message = "Cannot get " + url;
                if (count <= retries) {
                    logger.warn("Retry (" + count + "/" + retries + ") for call: " + message + " cause: " + e.getMessage());
                    waitForRetry();
                }
                else {
                    throw new ActuatorClientException(message, e);
                }
            }
        }
    }

    private void waitForRetry() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void heapdump(File path, String fileId) {
        // http://localhost:8080/actuator/heapdump
        String totalUrl = baseUrl + "/heapdump";
        File file = new File(path, "heapdump-" + fileId + "-" + fileTimeStamp() + ".hprof");
        downloadAndSave(file, totalUrl);
        logger.info("wrote heap dump to " + file);
    }

    private String fileTimeStamp() {
        return DATE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    public void threaddump(File path, String filename) {
        // http://localhost:8080/actuator/threaddump
        String totalUrl = baseUrl + "/threaddump";
        File file = new File(path, "threaddump-" + filename + "-" + fileTimeStamp() + ".txt");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/plain");
        downloadAndSave(file, totalUrl, headers);
        logger.info("wrote thread dump to " + file);
    }

    private void downloadAndSave(File file, String url) {
        downloadAndSave(file, url, Collections.emptyMap());
    }

    private void downloadAndSave(File file, String url, Map<String,String> headers) {
        try {
            FileOutputStream fos = new FileOutputStream(file);

            try (OutputStream outputStream = new BufferedOutputStream(fos)) {
                BinaryFileWriter writer = new BinaryFileWriter(outputStream);
                BinaryFileDownloader downloader = new BinaryFileDownloader(okHttpClient, writer);
                downloader.download(url, headers);
            }
        } catch (IOException e) {
            logger.error("Create heap dump and save heap dump failed.", e);
        }
    }

    private Stream<Variable> filterAndPrefixProperties(String prefix, List<String> propertyNames, Set<Map.Entry<String, Value>> propertiesSet) {
        return propertiesSet.stream()
            .filter(entry -> propertyNames.contains(entry.getKey()))
            .map(entry -> new Variable(prefix + ":" + entry.getKey(), entry.getValue().value));
    }

}

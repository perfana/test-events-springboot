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

import io.perfana.eventscheduler.api.config.EventConfig;
import io.perfana.eventscheduler.api.config.TestContext;
import net.jcip.annotations.NotThreadSafe;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@NotThreadSafe
public class SpringBootEventConfig extends EventConfig {

    private String myEventTags;
    private String actuatorPropPrefix = "actuator";
    private String actuatorBaseUrl;
    private String actuatorEnvProperties;

    public void setMyEventTags(String myEventTags) {
        this.myEventTags = myEventTags;
    }

    private List<String> createEnvProps() {
        return actuatorEnvProperties == null ? Collections.emptyList() : Arrays.asList(actuatorEnvProperties.split(","));
    }

    @Override
    public SpringBootEventContext toContext() {
        List<String> envProps = createEnvProps();
        return new SpringBootEventContext(super.toContext(), myEventTags, actuatorPropPrefix, actuatorBaseUrl, envProps);
    }

    @Override
    public SpringBootEventContext toContext(TestContext override) {
        List<String> envProps = createEnvProps();
        return new SpringBootEventContext(super.toContext(override), myEventTags, actuatorPropPrefix, actuatorBaseUrl, envProps);
    }

    @Override
    public String toString() {
        return "SpringBootEventConfig{" +
            "myEventTags='" + myEventTags + '\'' +
            ", actuatorPropPrefix='" + actuatorPropPrefix + '\'' +
            ", actuatorBaseUrl='" + actuatorBaseUrl + '\'' +
            ", actuatorEnvProperties='" + actuatorEnvProperties + '\'' +
            "} " + super.toString();
    }

    public String getActuatorBaseUrl() {
        return actuatorBaseUrl;
    }

    public void setActuatorBaseUrl(String actuatorBaseUrl) {
        this.actuatorBaseUrl = actuatorBaseUrl;
    }

    public String getActuatorEnvProperties() {
        return actuatorEnvProperties;
    }

    public void setActuatorEnvProperties(String actuatorEnvProperties) {
        this.actuatorEnvProperties = actuatorEnvProperties;
    }

    public String getActuatorPropPrefix() {
        return actuatorPropPrefix;
    }

    public void setActuatorPropPrefix(String actuatorPropPrefix) {
        this.actuatorPropPrefix = actuatorPropPrefix;
    }
}

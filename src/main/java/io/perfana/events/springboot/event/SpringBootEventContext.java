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

import io.perfana.eventscheduler.api.config.EventContext;
import net.jcip.annotations.Immutable;

import java.time.Duration;
import java.util.List;

@Immutable
public class SpringBootEventContext extends EventContext {

    private final String myEventTags;
    private final String actuatorPropPrefix;
    private final String actuatorBaseUrl;
    private final List<String> actuatorEnvProps;

    protected SpringBootEventContext(EventContext context, String myEventTags, String actuatorPropPrefix, String actuatorBaseUrl, List<String> actuatorEnvProps) {
        super(context, SpringBootEventFactory.class.getName(), true);
        this.myEventTags = myEventTags;
        this.actuatorPropPrefix = actuatorPropPrefix;
        this.actuatorBaseUrl = actuatorBaseUrl;
        this.actuatorEnvProps = actuatorEnvProps;
    }

    public String getMyEventTags() {
        return myEventTags;
    }

    public String getActuatorBaseUrl() {
        return actuatorBaseUrl;
    }
    public String getActuatorPropPrefix() {
        return actuatorPropPrefix;
    }

    public List<String> getActuatorEnvProperties() {
        return actuatorEnvProps;
    }

    @Override
    public String toString() {
        return "SpringBootEventConfig{" +
            ", myEventTags='" + myEventTags + '\'' +
            ", actuatorPropPrefix='" + actuatorPropPrefix + '\'' +
            ", actuatorBaseUrl='" + actuatorBaseUrl + '\'' +
            ", actuatorEnvProperties='" + actuatorEnvProps + '\'' +
            "} " + super.toString();
    }
}

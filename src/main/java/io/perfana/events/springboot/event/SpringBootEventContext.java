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

import java.util.List;

@Immutable
public class SpringBootEventContext extends EventContext {

    private final String tags;
    private final String actuatorPropPrefix;
    private final String actuatorBaseUrl;

    private final String dumpPath;

    private final List<String> actuatorEnvProps;

    protected SpringBootEventContext(EventContext context, String tags, String actuatorPropPrefix, String actuatorBaseUrl, List<String> actuatorEnvProps, String dumpPath) {
        super(context, SpringBootEventFactory.class.getName(), true);
        this.tags = tags;
        this.actuatorPropPrefix = actuatorPropPrefix;
        this.actuatorBaseUrl = actuatorBaseUrl;
        this.actuatorEnvProps = actuatorEnvProps;
        this.dumpPath = dumpPath;
    }

    public String getTags() {
        return tags;
    }

    public String getActuatorBaseUrl() {
        return actuatorBaseUrl;
    }

    /**
     * @deprecated: use tags instead
     */
    @Deprecated
    public String getActuatorPropPrefix() {
        return actuatorPropPrefix;
    }

    public List<String> getActuatorEnvProperties() {
        return actuatorEnvProps;
    }

    public String getDumpPath() {
        return dumpPath;
    }

    @Override
    public String toString() {
        return "SpringBootEventConfig{" +
            ", tags='" + tags + '\'' +
            ", actuatorPropPrefix(Deprecated)='" + actuatorPropPrefix + '\'' +
            ", actuatorBaseUrl='" + actuatorBaseUrl + '\'' +
            ", actuatorEnvProperties='" + actuatorEnvProps + '\'' +
            ", dumpPath='" + dumpPath + '\'' +
            "} " + super.toString();
    }
}

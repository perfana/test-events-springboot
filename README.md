# test-events-springboot
Fire test events for Spring Boot. Talks to actuator metrics and actions.

This events plugin reacts to the following custom events:
* `heapdump` - calls actuator heap dump endpoint and saves it to `dumpPath` (defaults to `java.io.tmpdir`)
* `threaddump` - calls actuator thread dump endpoint and saves it to `dumpPath` (defaults to `java.io.tmpdir`)

In the example below, there are two heap dump requests (5 and 60 seconds into the test run) and two stack dump requests (10 and 15 seconds into the test run).

The names of the dumps include the test run id and a time stamp.

In the test start event, it collects settings from actuator env endpoint, and broadcasts this to
other plugins. For instance, if you use the Perfana Java client plugin as well, this information
is automatically send to Perfana. The values are then stored with the current test run.

## use

```xml
<plugins>
    <plugin>
        <groupId>io.perfana</groupId>
        <artifactId>event-scheduler-maven-plugin</artifactId>
        <configuration>
            <eventSchedulerConfig>
                <debugEnabled>true</debugEnabled>
                <schedulerEnabled>true</schedulerEnabled>
                <failOnError>true</failOnError>
                <continueOnEventCheckFailure>true</continueOnEventCheckFailure>
                <eventScheduleScript>
                    PT5S|heapdump
                    PT10S|threaddump
                    PT15S|threaddump
                    PT1M|heapdump
                </eventScheduleScript>
                <testConfig>
                    <systemUnderTest>${systemUnderTest}</systemUnderTest>
                    <version>${version}</version>
                    <workload>${workload}</workload>
                    <testEnvironment>${testEnvironment}</testEnvironment>
                    <testRunId>${testRunId}</testRunId>
                    <buildResultsUrl>${buildResultsUrl}</buildResultsUrl>
                    <rampupTimeInSeconds>${rampupTimeInSeconds}</rampupTimeInSeconds>
                    <constantLoadTimeInSeconds>${constantLoadTimeInSeconds}</constantLoadTimeInSeconds>
                    <annotations>${annotations}</annotations>
                    <tags>${tags}</tags>
                </testConfig>
                <eventConfigs>
                    <eventConfig implementation="io.perfana.events.springboot.event.SpringBootEventConfig">
                        <name>SpringBootEventFrontend</name>
                        <actuatorPropPrefix>optimus-prime-fe</actuatorPropPrefix>
                        <actuatorBaseUrl>http://optimus-prime-fe:8080/actuator</actuatorBaseUrl>
                        <actuatorEnvProperties>java.runtime.version,JDK_JAVA_OPTIONS,afterburner.async_core_pool_size</actuatorEnvProperties>
                        <dumpPath>/test-data/dump-files/fontend/</dumpPath>
                    </eventConfig>
                    <eventConfig implementation="io.perfana.events.springboot.event.SpringBootEventConfig">
                        <name>SpringBootEventBackend</name>
                        <actuatorPropPrefix>optimus-prime-be</actuatorPropPrefix>
                        <actuatorBaseUrl>http://optimus-prime-be:8080/actuator</actuatorBaseUrl>
                        <actuatorEnvProperties>java.runtime.version,JDK_JAVA_OPTIONS,afterburner.async_core_pool_size</actuatorEnvProperties>
                        <dumpPath>/test-data/dump-files/backend/</dumpPath>
                    </eventConfig>
                </eventConfigs>
            </eventSchedulerConfig>
        </configuration>
        <dependencies>
            <dependency>
                <groupId>io.perfana</groupId>
                <artifactId>test-events-springboot</artifactId>
                <version>${test-events-springboot.version}</version>
            </dependency>
            <dependency>
                <groupId>io.perfana</groupId>
                <artifactId>perfana-java-client</artifactId>
                <version>${perfana-java-client.version}</version>
            </dependency>
        </dependencies>
    </plugin>
</plugins>
```

See also:
* https://github.com/perfana/event-scheduler-maven-plugin
* https://github.com/perfana/event-scheduler
* https://github.com/perfana/perfana-java-client

package io.perfana.events.springboot.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpringBootEventConfigTest {

    @Test
    void envPropertiesWithNewlinesAndWhitespacesToContext() {
        SpringBootEventConfig config = new SpringBootEventConfig();
        config.setActuatorEnvProperties("          property1\r\n,               \t\tproperty2\n   \n,  property3     \t   \n");
        SpringBootEventContext context = config.toContext();

        List<String> props = new ArrayList<>();
        props.add("property1");
        props.add("property2");
        props.add("property3");
        assertEquals(props, context.getActuatorEnvProperties());
    }
}
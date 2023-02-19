package io.perfana.events.springboot.actuator;

public class ActuatorClientException extends Exception {
    public ActuatorClientException(String message) {
        super(message);
    }

    public ActuatorClientException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

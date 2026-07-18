package com.logstream.exception;

public class KafkaUnavailableException extends RuntimeException {

    public KafkaUnavailableException(String message) {
        super(message);
    }
}

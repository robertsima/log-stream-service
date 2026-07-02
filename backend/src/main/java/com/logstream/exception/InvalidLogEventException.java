package com.logstream.exception;

public class InvalidLogEventException extends RuntimeException {

    public InvalidLogEventException(String message) {
        super(message);
    }
}

package com.kitchensink.api.exception;

public class StaleObjectException extends RuntimeException {
    public StaleObjectException(String m){ super(m); }
}

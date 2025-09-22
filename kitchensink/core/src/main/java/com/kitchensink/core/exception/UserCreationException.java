package com.kitchensink.core.exception;

import org.springframework.dao.DataAccessException;

public class UserCreationException extends RuntimeException {
    public UserCreationException(String s, DataAccessException e) {
        super(s, e);
    }

    public UserCreationException(String s, Exception e1) {
        super(s, e1);
    }
}

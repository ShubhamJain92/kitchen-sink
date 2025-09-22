package com.quickstarts.kitchensink.exception;

import org.springframework.dao.DataAccessException;

public class UserCreationException extends RuntimeException {
    public UserCreationException(String s, DataAccessException e) {
        super(s, e);
    }

    public UserCreationException(String s, Exception e1) {
        super(s, e1);
    }
}

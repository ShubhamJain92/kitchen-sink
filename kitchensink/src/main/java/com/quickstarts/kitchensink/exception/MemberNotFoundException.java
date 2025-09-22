package com.quickstarts.kitchensink.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String idOrEmail) {
        super("Member not found: " + idOrEmail);
    }
}

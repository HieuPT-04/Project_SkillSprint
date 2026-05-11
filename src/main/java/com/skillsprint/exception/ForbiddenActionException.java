package com.skillsprint.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends BaseException {

    public ForbiddenActionException(String message) {
        super("FORBIDDEN_ACTION", message, HttpStatus.FORBIDDEN);
    }
}

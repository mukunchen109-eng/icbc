package com.icbc.financialinfo.modules.review;

import org.springframework.http.HttpStatus;

public class ReviewOperationException extends RuntimeException {
    private final HttpStatus status;
    private final Object data;

    public ReviewOperationException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ReviewOperationException(HttpStatus status, String message, Object data) {
        super(message);
        this.status = status;
        this.data = data;
    }

    public HttpStatus status() {
        return status;
    }

    public Object data() {
        return data;
    }
}

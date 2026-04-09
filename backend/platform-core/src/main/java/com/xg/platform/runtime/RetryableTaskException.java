package com.xg.platform.runtime;

public class RetryableTaskException extends RuntimeException {

    public RetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}

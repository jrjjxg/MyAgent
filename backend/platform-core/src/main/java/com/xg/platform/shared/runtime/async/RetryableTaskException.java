package com.xg.platform.shared.runtime.async;

public class RetryableTaskException extends RuntimeException {

    public RetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}

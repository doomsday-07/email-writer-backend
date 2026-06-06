package com.email.writer.error;

public class GmailNotConnectedException extends RuntimeException {
    public GmailNotConnectedException(String message) {
        super(message);
    }
}

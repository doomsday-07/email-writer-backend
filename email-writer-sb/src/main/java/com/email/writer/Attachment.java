package com.email.writer;

public record Attachment(
        String filename,
        String contentType,
        byte[] bytes) {}

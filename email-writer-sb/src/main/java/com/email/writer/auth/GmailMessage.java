package com.email.writer.auth;

public record GmailMessage(
        String id,
        String from,
        String subject,
        String snippet,
        String date,
        String body) {
}
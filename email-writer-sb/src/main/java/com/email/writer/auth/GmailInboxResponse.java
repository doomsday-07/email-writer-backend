package com.email.writer.auth;

import java.util.List;

public record GmailInboxResponse(
        List<GmailMessage> messages,
        String nextPageToken,
        int resultSizeEstimate) {
}
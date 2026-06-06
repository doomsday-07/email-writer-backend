package com.email.writer.auth;

import com.email.writer.error.GmailNotConnectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailApiService {

    private static final String GMAIL_SEND_URI =
            "https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send";
    private static final long REFRESH_BUFFER_SECONDS = 300;

    private final RestClient restClient = RestClient.create();
    private final GmailTokenRepository repository;
    private final GoogleTokenService googleTokenService;

    public void send(String userId,
                     String userEmail,
                     String userName,
                     String to,
                     String subject,
                     String body) {
        String accessToken = getValidAccessToken(userId);
        String raw = buildRfc2822(userEmail, userName, to, subject, body);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String json = "{\"raw\":\"" + encoded + "\"}";

        try {
            restClient.post()
                    .uri(GMAIL_SEND_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Gmail send failed for user={}", userId, e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String getValidAccessToken(String userId) {
        GmailToken token = repository.findById(userId)
                .orElseThrow(() -> new GmailNotConnectedException(
                        "Gmail is not connected for this account. Click 'Connect Gmail' first."));

        if (token.getExpiresAt().isBefore(Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS))) {
            GoogleTokenService.TokenResponse resp =
                    googleTokenService.refreshAccessToken(token.getRefreshToken());
            if (resp == null || resp.access_token() == null) {
                throw new GmailNotConnectedException(
                        "Gmail refresh failed; please reconnect your account.");
            }
            token.setAccessToken(resp.access_token());
            long expiresIn = resp.expires_in() != null ? resp.expires_in() : 3600L;
            token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
            repository.save(token);
        }
        return token.getAccessToken();
    }

    private String buildRfc2822(String fromEmail, String fromName,
                                String to, String subject, String body) {
        String from = (fromName == null || fromName.isBlank())
                ? fromEmail
                : "\"" + fromName.replace("\"", "\\\"") + "\" <" + fromEmail + ">";
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + (body == null ? "" : body);
    }
}

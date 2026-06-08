package com.email.writer.auth;

import com.email.writer.error.GmailNotConnectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailApiService {

    private static final String GMAIL_SEND_URI =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private static final String GMAIL_API_BASE =
            "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final long REFRESH_BUFFER_SECONDS = 300;
    private static final int MAX_BODY_LENGTH = 10000;

    private final RestClient restClient = RestClient.create();
    private final GmailTokenRepository repository;
    private final GoogleTokenService googleTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GmailInboxResponse listMessages(String userId, int maxResults,
                                           String pageToken, String query) {
        String accessToken = getValidAccessToken(userId);
        try {
            StringBuilder uri = new StringBuilder(GMAIL_API_BASE
                    + "/messages?maxResults=" + maxResults);
            if (pageToken != null && !pageToken.isBlank()) {
                uri.append("&pageToken=").append(pageToken);
            }
            if (query != null && !query.isBlank()) {
                uri.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
            }

            String json = restClient.get()
                    .uri(uri.toString())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            if (json == null) return new GmailInboxResponse(List.of(), null, 0);

            JsonNode root = objectMapper.readTree(json);
            String nextPageToken = root.path("nextPageToken").asText(null);
            int resultSizeEstimate = root.path("resultSizeEstimate").asInt(0);
            JsonNode messages = root.path("messages");
            if (!messages.isArray()) return new GmailInboxResponse(List.of(), nextPageToken, resultSizeEstimate);

            List<GmailMessage> result = new ArrayList<>();
            for (JsonNode msg : messages) {
                String id = msg.path("id").asText();
                result.add(fetchMessageMetadata(accessToken, id));
            }
            return new GmailInboxResponse(result, nextPageToken, resultSizeEstimate);
        } catch (Exception e) {
            log.error("Failed to list Gmail messages for user={}", userId, e);
            throw new RuntimeException("Failed to fetch inbox: " + e.getMessage(), e);
        }
    }

    private GmailMessage fetchMessageMetadata(String accessToken, String messageId) {
        try {
            String json = restClient.get()
                    .uri(GMAIL_API_BASE + "/messages/" + messageId
                            + "?format=metadata&fields=id,snippet,payload/headers")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            if (json == null) return new GmailMessage(messageId, "", "", "", "", "");

            JsonNode root = objectMapper.readTree(json);
            String id = root.path("id").asText();
            String snippet = root.path("snippet").asText();
            JsonNode payload = root.path("payload");
            String from = extractHeader(payload, "From");
            String subject = extractHeader(payload, "Subject");
            String date = extractHeader(payload, "Date");
            return new GmailMessage(id, from, subject, snippet, date, "");
        } catch (Exception e) {
            log.warn("Failed to fetch metadata for message {}", messageId, e);
            return new GmailMessage(messageId, "", "", "", "", "");
        }
    }

    public GmailMessage getMessage(String userId, String messageId) {
        String accessToken = getValidAccessToken(userId);
        try {
            String json = restClient.get()
                    .uri(GMAIL_API_BASE + "/messages/" + messageId + "?format=full")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            if (json == null) throw new RuntimeException("Empty response from Gmail API");

            JsonNode root = objectMapper.readTree(json);
            String id = root.path("id").asText();
            String snippet = root.path("snippet").asText();
            JsonNode payload = root.path("payload");

            String from = extractHeader(payload, "From");
            String subject = extractHeader(payload, "Subject");
            String date = extractHeader(payload, "Date");
            String body = extractBody(payload);

            return new GmailMessage(id, from, subject, snippet, date, body);
        } catch (Exception e) {
            log.error("Failed to get Gmail message {} for user={}", messageId, userId, e);
            throw new RuntimeException("Failed to fetch email: " + e.getMessage(), e);
        }
    }

    public void sendWithAttachments(String userId,
                                     String userEmail,
                                     String userName,
                                     String to,
                                     String subject,
                                     String body,
                                     List<com.email.writer.Attachment> attachments) {
        try {
            String accessToken = getValidAccessToken(userId);

            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            String from = (userName == null || userName.isBlank())
                    ? userEmail
                    : "\"" + userName.replace("\"", "\\\"") + "\" <" + userEmail + ">";
            mimeMessage.setFrom(new InternetAddress(from));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            mimeMessage.setSubject(subject, "UTF-8");

            Multipart multipart = new MimeMultipart("mixed");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(body, "UTF-8");
            multipart.addBodyPart(textPart);

            for (com.email.writer.Attachment att : attachments) {
                MimeBodyPart attachPart = new MimeBodyPart();
                attachPart.setFileName(att.filename());
                attachPart.setDataHandler(new DataHandler(
                        new ByteArrayDataSource(att.bytes(), att.contentType())));
                multipart.addBodyPart(attachPart);
            }

            mimeMessage.setContent(multipart);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMessage.writeTo(baos);
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(baos.toByteArray());
            String json = "{\"raw\":\"" + encoded + "\"}";

            restClient.post()
                    .uri(GMAIL_SEND_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (GmailNotConnectedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gmail send with attachments failed for user={}", userId, e);
            String msg = e instanceof RestClientException
                    ? "Gmail API rejected the request: " + e.getMessage()
                    : "Failed to send email with attachments: " + e.getMessage();
            throw new RuntimeException(msg, e);
        }
    }

    public void send(String userId,
                     String userEmail,
                     String userName,
                     String to,
                     String subject,
                     String body) {
        try {
            String accessToken = getValidAccessToken(userId);
            String raw = buildRfc2822(userEmail, userName, to, subject, body);
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            String json = "{\"raw\":\"" + encoded + "\"}";

            restClient.post()
                    .uri(GMAIL_SEND_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (GmailNotConnectedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gmail send failed for user={}", userId, e);
            String msg = e instanceof RestClientException
                    ? "Gmail API rejected the request: " + e.getMessage()
                    : "Failed to send email: " + e.getMessage();
            throw new RuntimeException(msg, e);
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

    private String extractHeader(JsonNode payload, String name) {
        JsonNode headers = payload.path("headers");
        if (headers.isArray()) {
            for (JsonNode h : headers) {
                if (name.equalsIgnoreCase(h.path("name").asText())) {
                    return h.path("value").asText();
                }
            }
        }
        return "";
    }

    private String extractBody(JsonNode payload) {
        String mimeType = payload.path("mimeType").asText();
        JsonNode body = payload.path("body");
        JsonNode parts = payload.path("parts");

        if (parts.isArray() && !parts.isEmpty()) {
            String text = "";
            String html = "";
            for (JsonNode part : parts) {
                String partMime = part.path("mimeType").asText();
                if ("text/plain".equals(partMime)) {
                    text = decodeBody(part.path("body"));
                } else if ("text/html".equals(partMime)) {
                    html = decodeBody(part.path("body"));
                } else {
                    String nested = extractBody(part);
                    if (!nested.isEmpty() && text.isEmpty()) text = nested;
                }
            }
            String result = !text.isEmpty() ? text : html;
            return truncateAndSanitize(result);
        }

        if ("text/plain".equals(mimeType)) {
            return truncateAndSanitize(decodeBody(body));
        }
        if ("text/html".equals(mimeType)) {
            return truncateAndSanitize(decodeBody(body));
        }
        return truncateAndSanitize(decodeBody(body));
    }

    private String decodeBody(JsonNode bodyNode) {
        String data = bodyNode.path("data").asText();
        if (data.isEmpty()) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(data);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode body data", e);
            return "";
        }
    }

    private String truncateAndSanitize(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("<[^>]*>", "");
        if (clean.length() > MAX_BODY_LENGTH) {
            clean = clean.substring(0, MAX_BODY_LENGTH);
        }
        return clean;
    }

    public void starMessage(String userId, String messageId) {
        modifyMessageLabels(userId, messageId, List.of("STARRED"), List.of());
    }

    public void unstarMessage(String userId, String messageId) {
        modifyMessageLabels(userId, messageId, List.of(), List.of("STARRED"));
    }

    public void archiveMessage(String userId, String messageId) {
        modifyMessageLabels(userId, messageId, List.of(), List.of("INBOX"));
    }

    public void unarchiveMessage(String userId, String messageId) {
        modifyMessageLabels(userId, messageId, List.of("INBOX"), List.of());
    }

    public void trashMessage(String userId, String messageId) {
        modifyMessageLabels(userId, messageId, List.of("TRASH"), List.of());
    }

    private void modifyMessageLabels(String userId, String messageId,
                                      List<String> addLabelIds,
                                      List<String> removeLabelIds) {
        String accessToken = getValidAccessToken(userId);
        try {
            Map<String, List<String>> body = new HashMap<>();
            body.put("addLabelIds", addLabelIds);
            body.put("removeLabelIds", removeLabelIds);
            String jsonBody = objectMapper.writeValueAsString(body);

            restClient.post()
                    .uri(GMAIL_API_BASE + "/messages/" + messageId + "/modify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to modify labels for message {} for user={}",
                    messageId, userId, e);
            throw new RuntimeException("Failed to modify email: " + e.getMessage(), e);
        }
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

package com.email.writer;

import com.email.writer.auth.GmailApiService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final GmailApiService gmailApiService;

    public EmailGeneratorService(@Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey,
                                 GmailApiService gmailApiService) {
        this.apiKey = geminiApiKey;
        this.webClient = WebClient.create(baseUrl);
        this.objectMapper = new ObjectMapper();
        this.gmailApiService = gmailApiService;
    }

    public String generateEmailReply(EmailRequest emailRequest, String authenticatedUser) {
        log.debug("generateEmailReply user={} tone={} contentLen={}",
                authenticatedUser,
                emailRequest.getTone(),
                emailRequest.getEmailContent() != null ? emailRequest.getEmailContent().length() : 0);
        String prompt = buildReplyPrompt(emailRequest);
        return callGemini(prompt);
    }

    public String translateEmail(EmailRequest emailRequest, String authenticatedUser) {
        String targetLanguage = emailRequest.getTargetLanguage();
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("targetLanguage is required for translations.");
        }
        String sourceLanguage = emailRequest.getSourceLanguage();
        String emailContent = emailRequest.getEmailContent();

        log.debug("translateEmail user={} target={} source={} contentLen={}",
                authenticatedUser, targetLanguage, sourceLanguage,
                emailContent != null ? emailContent.length() : 0);

        String prompt = buildTranslationPrompt(emailContent,
                sourceLanguage == null ? null : sourceLanguage.trim(),
                targetLanguage.trim());
        return callGemini(prompt);
    }

    public void sendEmail(EmailRequest emailRequest, String userId, String userEmail, String userName) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalStateException("Authenticated user has no email claim; cannot send.");
        }
        gmailApiService.send(userId, userEmail, userName,
                emailRequest.getRecipientEmail(),
                emailRequest.getEmailSubject(),
                emailRequest.getMessageBody());
    }

    private String callGemini(String prompt) {
        String requestBody = createRequestBody(prompt);

        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/gemini-3-flash-preview:generateContent")
                            .build())
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Empty response from Gemini API");
            }
            return extractResponseContent(response);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    private String createRequestBody(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize Gemini request payload", e);
        }
    }

    private String extractResponseContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (root.has("error")) {
                String errorMessage = root.path("error").path("message").asText();
                throw new RuntimeException("Gemini API Error: " + errorMessage);
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("No candidates in Gemini response: " + response);
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");

            if (!parts.isArray() || parts.isEmpty()) {
                throw new RuntimeException("No parts in Gemini response: " + response);
            }

            return parts.get(0).path("text").asText();
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private String buildReplyPrompt(EmailRequest emailRequest) {
        String tone = emailRequest.getTone();
        if (tone == null || tone.isBlank()) {
            tone = "professional";
        }
        String emailContent = emailRequest.getEmailContent();
        if (emailContent == null) {
            emailContent = "";
        }
        return """
                You are an intelligent email reply generator.

                Read the email carefully and generate a relevant, context-aware reply.
                Do NOT assume the email is incomplete unless it is empty or meaningless.
                Do NOT mention technical errors, placeholders, or missing content.

                Tone: %s

                Original Email:
                %s
                """.formatted(tone, emailContent);
    }

    private String buildTranslationPrompt(String emailContent, String sourceLanguage, String targetLanguage) {
        String content = emailContent == null ? "" : emailContent;
        String targetLanguageName = convertLanguageCodeToName(targetLanguage);
        String sourceLanguageName = sourceLanguage != null ? convertLanguageCodeToName(sourceLanguage) : null;

        String instruction;
        if (sourceLanguageName == null || sourceLanguageName.isBlank()) {
            instruction = "Translate the following email into %s, automatically detecting the original language. Return only the translated text."
                    .formatted(targetLanguageName);
        } else {
            instruction = "Translate the following email from %s to %s, preserving tone and meaning. Return only the translated text."
                    .formatted(sourceLanguageName, targetLanguageName);
        }

        return """
                %s

                Original Email:
                %s
                """.formatted(instruction, content);
    }

    private String convertLanguageCodeToName(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return languageCode;
        }
        return switch (languageCode.toLowerCase().trim()) {
            case "en" -> "English";
            case "hi" -> "Hindi";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "de" -> "German";
            case "ja" -> "Japanese";
            case "zh" -> "Chinese";
            case "pt" -> "Portuguese";
            case "ru" -> "Russian";
            case "ar" -> "Arabic";
            default -> languageCode;
        };
    }
}

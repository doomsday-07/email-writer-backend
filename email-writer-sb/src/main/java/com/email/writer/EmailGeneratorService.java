package com.email.writer;

import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public EmailGeneratorService(@Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey,
                                 JavaMailSender mailSender,
                                 @Value("${spring.mail.username}") String mailFrom) {
        this.apiKey = geminiApiKey;
        this.webClient = WebClient.create(baseUrl);
        this.objectMapper = new ObjectMapper();
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        String prompt = buildReplyPrompt(emailRequest);
        return callGemini(prompt);
    }

    public String translateEmail(EmailRequest emailRequest) {
        String targetLanguage = emailRequest.getTargetLanguage();
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("targetLanguage is required for translations.");
        }

        String sourceLanguage = emailRequest.getSourceLanguage();
        String emailContent = emailRequest.getEmailContent();
        
        System.out.println("Translation Request - Content length: " + (emailContent != null ? emailContent.length() : 0) + 
                          ", Target Language: " + targetLanguage + 
                          ", Source Language: " + sourceLanguage);
        
        String prompt = buildTranslationPrompt(emailContent,
                sourceLanguage == null ? null : sourceLanguage.trim(),
                targetLanguage.trim());
        
        System.out.println("Translation Prompt: " + prompt.substring(0, Math.min(200, prompt.length())) + "...");
        
        return callGemini(prompt);
    }

    public String sendEmail(EmailRequest emailRequest) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("Sender email is not configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(emailRequest.getRecipientEmail());
        message.setSubject(emailRequest.getEmailSubject());
        message.setText(emailRequest.getMessageBody());

        mailSender.send(message);
        return "Email sent successfully.";
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
            System.err.println("Gemini API Error: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("Error parsing response: " + e.getMessage());
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
        
        // Convert language codes to full names
        String targetLanguageName = convertLanguageCodeToName(targetLanguage);
        String sourceLanguageName = sourceLanguage != null ? convertLanguageCodeToName(sourceLanguage) : null;
        
        String instruction;
        if (sourceLanguageName == null || sourceLanguageName.isBlank()) {
            instruction = "Translate the following email into %s, automatically detecting the original language. Return only the translated text.";
            instruction = instruction.formatted(targetLanguageName);
        } else {
            instruction = "Translate the following email from %s to %s, preserving tone and meaning. Return only the translated text.";
            instruction = instruction.formatted(sourceLanguageName, targetLanguageName);
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
            default -> languageCode; // Return as-is if not recognized
        };
    }
}

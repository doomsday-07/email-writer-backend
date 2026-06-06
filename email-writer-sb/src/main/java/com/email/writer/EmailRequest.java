package com.email.writer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailRequest {

    @NotBlank(message = "emailContent must not be blank")
    @Size(max = 50_000, message = "emailContent must be <= 50000 chars")
    private String emailContent;

    @Size(max = 50, message = "tone must be <= 50 chars")
    private String tone;

    @Size(max = 50, message = "targetLanguage must be <= 50 chars")
    private String targetLanguage;

    @Size(max = 50, message = "sourceLanguage must be <= 50 chars")
    private String sourceLanguage;

    @Email(message = "recipientEmail must be a valid email")
    @Size(max = 254, message = "recipientEmail must be <= 254 chars")
    private String recipientEmail;

    @Size(max = 998, message = "emailSubject must be <= 998 chars")
    private String emailSubject;

    @Size(max = 50_000, message = "messageBody must be <= 50000 chars")
    private String messageBody;
}

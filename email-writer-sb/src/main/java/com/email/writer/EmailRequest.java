package com.email.writer;

import lombok.Data;

@Data
public class EmailRequest {
    private String emailContent;
    private String tone;
    private String targetLanguage;
    private String sourceLanguage;
    private String recipientEmail;
    private String emailSubject;
    private String messageBody;
}

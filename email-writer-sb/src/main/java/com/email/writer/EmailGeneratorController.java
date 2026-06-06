package com.email.writer;

import com.email.writer.error.GmailNotConnectedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailGeneratorController {

    private final EmailGeneratorService emailGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateEmailReply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailRequest emailRequest) {
        if (emailRequest.getEmailContent() == null || emailRequest.getEmailContent().isBlank()) {
            throw new IllegalArgumentException("emailContent must not be blank");
        }
        String email = jwt.getClaimAsString("email");
        String response = emailGeneratorService.generateEmailReply(emailRequest, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/translate")
    public ResponseEntity<String> translateEmail(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailRequest emailRequest) {
        if (emailRequest.getEmailContent() == null || emailRequest.getEmailContent().isBlank()) {
            throw new IllegalArgumentException("emailContent must not be blank");
        }
        if (emailRequest.getTargetLanguage() == null || emailRequest.getTargetLanguage().isBlank()) {
            throw new IllegalArgumentException("targetLanguage is required for translations.");
        }
        String email = jwt.getClaimAsString("email");
        String translation = emailGeneratorService.translateEmail(emailRequest, email);
        return ResponseEntity.ok(translation);
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailRequest emailRequest) {
        if (emailRequest.getRecipientEmail() == null || emailRequest.getRecipientEmail().isBlank()) {
            throw new IllegalArgumentException("recipientEmail is required for sending.");
        }
        if (emailRequest.getEmailSubject() == null || emailRequest.getEmailSubject().isBlank()) {
            throw new IllegalArgumentException("emailSubject is required for sending.");
        }
        if (emailRequest.getMessageBody() == null || emailRequest.getMessageBody().isBlank()) {
            throw new IllegalArgumentException("messageBody is required for sending.");
        }

        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (email == null) {
            throw new GmailNotConnectedException("Authenticated user has no email claim.");
        }
        emailGeneratorService.sendEmail(emailRequest, userId, email, name);
        return ResponseEntity.ok("Email sent successfully.");
    }
}

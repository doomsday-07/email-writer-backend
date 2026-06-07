package com.email.writer;

import com.email.writer.auth.GmailApiService;
import com.email.writer.auth.GmailInboxResponse;
import com.email.writer.auth.GmailMessage;
import com.email.writer.error.GmailNotConnectedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailGeneratorController {

    private final EmailGeneratorService emailGeneratorService;
    private final GmailApiService gmailApiService;

    @GetMapping("/inbox")
    public ResponseEntity<GmailInboxResponse> listInbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int maxResults,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false) String q) {
        String userId = jwt.getSubject();
        GmailInboxResponse response = gmailApiService.listMessages(
                userId, maxResults, pageToken, q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inbox/{messageId}")
    public ResponseEntity<GmailMessage> getInboxMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        String userId = jwt.getSubject();
        GmailMessage message = gmailApiService.getMessage(userId, messageId);
        return ResponseEntity.ok(message);
    }

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

    @PostMapping("/{messageId}/star")
    public ResponseEntity<Void> starMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        gmailApiService.starMessage(jwt.getSubject(), messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/unstar")
    public ResponseEntity<Void> unstarMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        gmailApiService.unstarMessage(jwt.getSubject(), messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/archive")
    public ResponseEntity<Void> archiveMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        gmailApiService.archiveMessage(jwt.getSubject(), messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/unarchive")
    public ResponseEntity<Void> unarchiveMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        gmailApiService.unarchiveMessage(jwt.getSubject(), messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/trash")
    public ResponseEntity<Void> trashMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId) {
        gmailApiService.trashMessage(jwt.getSubject(), messageId);
        return ResponseEntity.ok().build();
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

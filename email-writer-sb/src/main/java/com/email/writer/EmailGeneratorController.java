package com.email.writer;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
@AllArgsConstructor
@CrossOrigin(origins = "*")

public class EmailGeneratorController {
    private final EmailGeneratorService emailGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateEmailReply(@RequestBody EmailRequest emailRequest) {
        String response = emailGeneratorService.generateEmailReply(emailRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/translate")
    public ResponseEntity<String> translateEmail(@RequestBody EmailRequest emailRequest) {
        if (emailRequest.getTargetLanguage() == null || emailRequest.getTargetLanguage().isBlank()) {
            return ResponseEntity.badRequest().body("targetLanguage is required for translations.");
        }

        String translation = emailGeneratorService.translateEmail(emailRequest);
        return ResponseEntity.ok(translation);
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody EmailRequest emailRequest) {
        if (emailRequest.getRecipientEmail() == null || emailRequest.getRecipientEmail().isBlank()) {
            return ResponseEntity.badRequest().body("recipientEmail is required for sending.");
        }
        if (emailRequest.getEmailSubject() == null || emailRequest.getEmailSubject().isBlank()) {
            return ResponseEntity.badRequest().body("emailSubject is required for sending.");
        }
        if (emailRequest.getMessageBody() == null || emailRequest.getMessageBody().isBlank()) {
            return ResponseEntity.badRequest().body("messageBody is required for sending.");
        }

        String result = emailGeneratorService.sendEmail(emailRequest);
        return ResponseEntity.ok(result);
    }
}

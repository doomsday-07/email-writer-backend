package com.email.writer.auth;

import com.email.writer.error.GmailNotConnectedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final GoogleTokenService googleTokenService;
    private final GmailTokenRepository repository;

    public record ExchangeRequest(
            @NotBlank String code,
            @NotBlank String redirectUri
    ) {}

    public record StatusResponse(boolean gmailConnected) {}

    @PostMapping("/exchange")
    public ResponseEntity<StatusResponse> exchange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ExchangeRequest req) {

        String userId = jwt.getSubject();
        GoogleTokenService.TokenResponse tokenResp =
                googleTokenService.exchangeCode(req.code(), req.redirectUri());

        if (tokenResp == null || tokenResp.access_token() == null) {
            throw new GmailNotConnectedException("Token exchange returned no access_token");
        }
        if (tokenResp.refresh_token() == null) {
            throw new GmailNotConnectedException(
                    "Token exchange returned no refresh_token. " +
                    "Re-consent with access_type=offline and prompt=consent.");
        }

        GmailToken existing = repository.findById(userId).orElse(null);
        GmailToken token = existing != null
                ? existing
                : GmailToken.builder().userId(userId).createdAt(Instant.now()).build();

        token.setAccessToken(tokenResp.access_token());
        token.setRefreshToken(tokenResp.refresh_token());
        long expiresIn = tokenResp.expires_in() != null ? tokenResp.expires_in() : 3600L;
        token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
        token.setScopes(tokenResp.scope() != null ? tokenResp.scope() : "");
        token.setUpdatedAt(Instant.now());
        repository.save(token);

        return ResponseEntity.ok(new StatusResponse(true));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean connected = repository.findById(userId)
                .map(t -> t.getRefreshToken() != null
                        && !t.getRefreshToken().isBlank()
                        && t.getExpiresAt() != null)
                .orElse(false);
        return ResponseEntity.ok(new StatusResponse(connected));
    }
}

package com.email.writer.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Service
@Slf4j
public class GoogleTokenService {

    private final RestClient restClient = RestClient.builder().build();

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    public record TokenResponse(
            String access_token,
            String refresh_token,
            Long expires_in,
            String scope,
            String id_token,
            String token_type
    ) {}

    public TokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", Objects.requireNonNull(code, "code"));
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", Objects.requireNonNull(redirectUri, "redirectUri"));
        form.add("grant_type", "authorization_code");

        return restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", Objects.requireNonNull(refreshToken, "refreshToken"));
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        return restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }
}

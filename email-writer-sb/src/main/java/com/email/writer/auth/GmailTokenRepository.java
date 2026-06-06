package com.email.writer.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailTokenRepository extends JpaRepository<GmailToken, String> {
}

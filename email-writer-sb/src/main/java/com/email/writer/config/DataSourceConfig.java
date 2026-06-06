package com.email.writer.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String rawUrl = System.getenv("DATABASE_URL");
        if (rawUrl != null && (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://"))) {
            URI uri = URI.create(rawUrl);
            String userInfo = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            String jdbcUrl = "jdbc:postgresql://" + host;
            if (port > 0) {
                jdbcUrl += ":" + port;
            }
            jdbcUrl += path;
            jdbcUrl += (path.contains("?") ? "&" : "?") + "sslmode=require";

            String username = null;
            String password = null;
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                if (colon > 0) {
                    username = userInfo.substring(0, colon);
                    password = userInfo.substring(colon + 1);
                } else {
                    username = userInfo;
                }
            }

            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        }
        return DataSourceBuilder.create()
                .url(System.getenv().getOrDefault("DATABASE_URL",
                        "jdbc:h2:mem:emailwriter;DB_CLOSE_DELAY=-1"))
                .username(System.getenv().getOrDefault("DATABASE_USERNAME", "sa"))
                .password(System.getenv().getOrDefault("DATABASE_PASSWORD", ""))
                .driverClassName(System.getenv().getOrDefault("DATABASE_DRIVER", "org.h2.Driver"))
                .build();
    }
}

package com.email.writer.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties props) {
        String url = props.getUrl();
        if (url != null && url.startsWith("postgres://")) {
            URI uri = URI.create(url);
            String userInfo = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            String jdbcUrl = "jdbc:postgresql://" + host;
            if (port > 0) {
                jdbcUrl += ":" + port;
            }
            jdbcUrl += path;
            jdbcUrl += "?sslmode=require";

            String username = props.getUsername();
            String password = props.getPassword();
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                if (username == null || "sa".equals(username)) {
                    username = parts[0];
                }
                if ((password == null || password.isEmpty()) && parts.length > 1) {
                    password = parts[1];
                }
            }

            return com.zaxxer.hikari.HikariDataSource.class.cast(
                    props.initializeDataSourceBuilder()
                            .type(HikariDataSource.class)
                            .url(jdbcUrl)
                            .username(username)
                            .password(password)
                            .driverClassName("org.postgresql.Driver")
                            .build()
            );
        }
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}

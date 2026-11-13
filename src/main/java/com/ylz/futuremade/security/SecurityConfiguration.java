package com.ylz.futuremade.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.ylz.futuremade.identity.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfiguration {
    public static final String ISSUER = "meetflow";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // 仅接受 Authorization 头中的 Bearer Token，不用 Cookie 认证。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.css", "/app.js").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    var account = mapper.findByUsername(token.getSubject());
                    Number userId = token.getClaim("uid");
                    if (account == null || !account.enabled() || userId == null || account.id() != userId.longValue()) {
                        throw new BadCredentialsException("账号不可用");
                    }
                    var authorities = mapper.findRoleCodes(account.id()).stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                    return UsernamePasswordAuthenticationToken.authenticated(account.username(), null, authorities);
                }))).build();
    }

    @Bean
    SecretKey jwtSecret(@Value("${meetflow.jwt.secret-base64:}") String base64) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT 密钥必须是 Base64 编码", ex); }
        if (bytes.length < 32) throw new IllegalStateException("JWT 密钥解码后至少需要 32 字节");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var defaultValidator = JwtValidators.createDefaultWithIssuer(ISSUER);
        decoder.setJwtValidator(token -> {
            var result = defaultValidator.validate(token);
            if (result.hasErrors()) return result;
            if (token.getExpiresAt() == null || token.getSubject() == null || token.getSubject().isBlank()
                    || !(token.getClaims().get("uid") instanceof Number)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }
}

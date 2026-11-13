package com.ylz.futuremade.identity.controller;


import com.ylz.futuremade.identity.model.dto.LoginRequest;
import com.ylz.futuremade.identity.mapper.AccountMapper;
import com.ylz.futuremade.security.SecurityConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * 身份认证控制器：提供用户名密码登录及当前登录用户信息查询。
 */
@RestController
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final AccountMapper mapper;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder encoder, AccountMapper mapper) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.mapper = mapper;
    }

    /** 验证账号密码并签发 JWT 访问令牌。 */
    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest input) {
        if (input.getUsername() == null || input.getUsername().isBlank() || input.getPassword() == null || input.getPassword().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入用户名和密码");
        }
        try {
            var auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.getUsername(), input.getPassword()));
            var account = mapper.findByUsername(auth.getName());
            if (account == null || !account.enabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用");
            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER).subject(account.username())
                    .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("uid", account.id()).build();
            String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            return Map.of("accessToken", jwt, "tokenType", "Bearer", "expiresIn", 3600, "username", account.username());
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
    }
    /** 查询当前登录用户的用户名和系统角色。 */
    @GetMapping("/api/auth/me") public Map<String, Object> currentUser(org.springframework.security.core.Authentication principal) { return Map.of("username", principal.getName(), "roles", principal.getAuthorities().stream().map(a -> a.getAuthority()).toList()); }
}

package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final AuthProperties properties;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(AuthProperties properties, TokenGenerator generator, TokenHashing hashing) throws Exception {
        this.properties = properties;
        this.generator = generator;
        this.hashing = hashing;
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();
        var rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(properties.jwt().keyId())
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }

    public TokenPair generate(AuthUserView user, UUID sessionId, Instant now) {
        UUID accessTokenJti = UUID.randomUUID();
        UUID refreshTokenJti = UUID.randomUUID();
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(java.util.List.of(properties.audience()))
                .subject(user.id().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(accessExpiresAt)
                .id(accessTokenJti.toString())
                .claim("preferred_username", user.username())
                .claim("roles", user.roles())
                .claim("perm_v", user.permVersion())
                .claim("sid", sessionId.toString())
                .claim("typ", "access")
                .build();
        JwsHeader header = JwsHeader.with(() -> "RS256").keyId(properties.jwt().keyId()).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        String refreshToken = generator.opaqueToken();
        return new TokenPair(
                accessToken,
                refreshToken,
                hashing.hash(refreshToken),
                accessTokenJti,
                refreshTokenJti,
                now,
                accessExpiresAt,
                refreshExpiresAt);
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    public String hashRefresh(String raw) {
        return hashing.hash(raw);
    }
}

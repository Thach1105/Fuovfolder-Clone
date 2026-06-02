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
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final AuthProperties properties; private final TokenGenerator generator; private final TokenHashing hashing; private final JwtEncoder encoder; private final JwtDecoder decoder;
    public JwtService(AuthProperties properties, TokenGenerator generator, TokenHashing hashing) throws Exception { this.properties=properties;this.generator=generator;this.hashing=hashing;var kpg=KeyPairGenerator.getInstance("RSA");kpg.initialize(2048);var kp=kpg.generateKeyPair();var rsa=new RSAKey.Builder((RSAPublicKey)kp.getPublic()).privateKey((RSAPrivateKey)kp.getPrivate()).keyID(properties.jwt().keyId()).build();JWKSource<SecurityContext> src=new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsa));this.encoder=new NimbusJwtEncoder(src);this.decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)kp.getPublic()).build(); }
    public TokenPair generate(AuthUserView user, UUID sessionId, Instant now){UUID atJti=UUID.randomUUID();UUID rtJti=UUID.randomUUID();Instant atExp=now.plus(properties.accessTokenTtl());Instant rtExp=now.plus(properties.refreshTokenTtl());JwtClaimsSet claims=JwtClaimsSet.builder().issuer(properties.issuer()).audience(java.util.List.of(properties.audience())).subject(user.id().toString()).issuedAt(now).notBefore(now).expiresAt(atExp).id(atJti.toString()).claim("preferred_username",user.username()).claim("roles",user.roles()).claim("sid",sessionId.toString()).claim("typ","access").build();JwsHeader header=JwsHeader.with(() -> "RS256").keyId(properties.jwt().keyId()).build();String at=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();String rt=generator.opaqueToken();return new TokenPair(at,rt,hashing.hash(rt),atJti,rtJti,now,atExp,rtExp);} 
    public Jwt decode(String token){return decoder.decode(token);} public String hashRefresh(String raw){return hashing.hash(raw);} }

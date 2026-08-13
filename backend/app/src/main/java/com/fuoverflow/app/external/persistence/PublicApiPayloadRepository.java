package com.fuoverflow.app.external.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PublicApiPayloadRepository extends JpaRepository<PublicApiPayloadEntity, UUID> {
}

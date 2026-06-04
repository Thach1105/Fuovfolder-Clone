package com.fuoverflow.award.application;

import com.fuoverflow.award.api.dto.PointsLedgerEntryResponse;
import com.fuoverflow.award.api.dto.PointsLedgerPageResponse;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PointsQueryService {
    private final PointsLedgerRepository ledgerRepository;

    public PointsQueryService(PointsLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public PointsLedgerPageResponse listLedger(UUID userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<PointsLedgerEntity> result = ledgerRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, safeSize));
        return new PointsLedgerPageResponse(
                result.getContent().stream().map(this::toEntry).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private PointsLedgerEntryResponse toEntry(PointsLedgerEntity e) {
        return new PointsLedgerEntryResponse(
                e.getId(),
                e.getDelta(),
                e.getReason(),
                e.getSourceType(),
                e.getSourceId(),
                e.getCreatedAt());
    }
}

package com.fuoverflow.deposit.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.deposit.api.dto.AdminDepositTierResponse;
import com.fuoverflow.deposit.api.dto.CreateDepositTierRequest;
import com.fuoverflow.deposit.api.dto.UpdateDepositTierRequest;
import com.fuoverflow.deposit.persistence.DepositTierEntity;
import com.fuoverflow.deposit.persistence.DepositTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DepositTierAdminService {

    private final DepositTierRepository repository;

    public DepositTierAdminService(DepositTierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdminDepositTierResponse> list() {
        return repository.findAll().stream()
                .map(AdminDepositTierResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDepositTierResponse get(UUID id) {
        return AdminDepositTierResponse.from(load(id));
    }

    @Transactional
    public AdminDepositTierResponse create(CreateDepositTierRequest request) {
        Instant now = Instant.now();
        DepositTierEntity entity = DepositTierEntity.create(
                request.label(),
                request.amountVnd(),
                request.points(),
                request.bonusPercent(),
                request.isActive(),
                request.sortOrder(),
                now);
        DepositTierEntity saved = repository.save(entity);
        return AdminDepositTierResponse.from(saved);
    }

    @Transactional
    public AdminDepositTierResponse update(UUID id, UpdateDepositTierRequest request) {
        DepositTierEntity entity = load(id);
        Instant now = Instant.now();
        entity.update(
                request.label() != null ? request.label() : entity.getLabel(),
                request.amountVnd() != null ? request.amountVnd() : entity.getAmountVnd(),
                request.points() != null ? request.points() : entity.getPoints(),
                request.bonusPercent() != null ? request.bonusPercent() : entity.getBonusPercent(),
                request.active() != null ? request.active() : entity.isActive(),
                request.sortOrder() != null ? request.sortOrder() : entity.getSortOrder(),
                now);
        return AdminDepositTierResponse.from(entity);
    }

    @Transactional
    public AdminDepositTierResponse toggle(UUID id) {
        DepositTierEntity entity = load(id);
        entity.update(
                entity.getLabel(),
                entity.getAmountVnd(),
                entity.getPoints(),
                entity.getBonusPercent(),
                !entity.isActive(),
                entity.getSortOrder(),
                Instant.now());
        return AdminDepositTierResponse.from(entity);
    }

    private DepositTierEntity load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("DEPOSIT_TIER_NOT_FOUND", "Deposit tier not found: " + id));
    }
}

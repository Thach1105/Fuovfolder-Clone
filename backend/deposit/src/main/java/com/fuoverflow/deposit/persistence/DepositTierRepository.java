package com.fuoverflow.deposit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositTierRepository extends JpaRepository<DepositTierEntity, UUID> {
    List<DepositTierEntity> findByIsActiveTrueOrderBySortOrderAscAmountVndAsc();
}

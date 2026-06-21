package com.fuoverflow.deposit.application;

import com.fuoverflow.deposit.api.dto.DepositTierResponse;
import com.fuoverflow.deposit.persistence.DepositTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepositTierQueryService {

    private final DepositTierRepository repository;

    public DepositTierQueryService(DepositTierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DepositTierResponse> listActive() {
        return repository.findByIsActiveTrueOrderBySortOrderAscAmountVndAsc().stream()
                .map(DepositTierResponse::from)
                .toList();
    }
}

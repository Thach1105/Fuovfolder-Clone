package com.fuoverflow.deposit.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.deposit.api.dto.DepositTierResponse;
import com.fuoverflow.deposit.application.DepositTierQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deposit")
public class DepositTierController {

    private final DepositTierQueryService service;

    public DepositTierController(DepositTierQueryService service) {
        this.service = service;
    }

    @GetMapping("/tiers")
    public ApiResponse<List<DepositTierResponse>> list() {
        return ApiResponse.ok(service.listActive());
    }
}

package com.remitlytics.core_engine.controller;

import com.remitlytics.core_engine.dto.TrialBalanceResponse;
import com.remitlytics.core_engine.service.LedgerReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerReportingController {

    private final LedgerReportingService ledgerReportingService;

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance(
            @RequestHeader("X-API-KEY") String apiKey) {
        TrialBalanceResponse trialBalance = ledgerReportingService.generateTrialBalance(apiKey);
        return ResponseEntity.ok(trialBalance);
    }
}
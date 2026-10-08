package com.smartbanking.user.controller;

import com.smartbanking.account.model.AccountStatus;
import com.smartbanking.account.service.AccountService;
import com.smartbanking.audit.model.AuditLog;
import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.fraud.dto.FraudFlagResponse;
import com.smartbanking.fraud.model.FraudFlag;
import com.smartbanking.fraud.service.FraudService;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import com.smartbanking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AuditLogService auditLogService;
    private final FraudService fraudService;
    private final AccountService accountService;
    private final UserRepository userRepository;

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditLog> logs = auditLogService.getAllLogs(page, size);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/fraud/flags")
    public ResponseEntity<List<FraudFlagResponse>> getFraudFlags() {
        List<FraudFlag> flags = fraudService.getUnresolvedFlags();
        List<FraudFlagResponse> responses = flags.stream()
                .map(this::mapToFraudFlagResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/fraud/flags/{id}/resolve")
    public ResponseEntity<Void> resolveFraudFlag(
            @PathVariable Long id,
            @RequestParam boolean approve,
            @RequestParam String notes,
            Authentication authentication) {
        fraudService.resolveFraudFlag(id, approve, notes, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/accounts/{accountNumber}/status")
    public ResponseEntity<Void> updateAccountStatus(
            @PathVariable String accountNumber,
            @RequestParam AccountStatus status,
            Authentication authentication) {
        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        accountService.setAccountStatus(accountNumber, status, admin);
        return ResponseEntity.ok().build();
    }

    private FraudFlagResponse mapToFraudFlagResponse(FraudFlag flag) {
        return FraudFlagResponse.builder()
                .id(flag.getId())
                .transactionNumber(flag.getTransaction().getTransactionNumber())
                .sourceAccountNumber(flag.getTransaction().getSourceAccount().getAccountNumber())
                .targetAccountNumber(flag.getTransaction().getTargetAccount().getAccountNumber())
                .amount(flag.getTransaction().getAmount())
                .reason(flag.getReason())
                .flaggedAt(flag.getFlaggedAt())
                .resolved(flag.isResolved())
                .resolvedBy(flag.getResolvedBy() != null ? flag.getResolvedBy().getUsername() : null)
                .resolvedAt(flag.getResolvedAt())
                .resolutionNotes(flag.getResolutionNotes())
                .build();
    }
}

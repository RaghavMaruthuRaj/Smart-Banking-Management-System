package com.smartbanking.fraud.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudFlagResponse {
    private Long id;
    private String transactionNumber;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime flaggedAt;
    private boolean resolved;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
}

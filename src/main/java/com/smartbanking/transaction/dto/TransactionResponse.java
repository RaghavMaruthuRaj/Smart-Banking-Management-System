package com.smartbanking.transaction.dto;

import com.smartbanking.transaction.model.TransactionCategory;
import com.smartbanking.transaction.model.TransactionStatus;
import com.smartbanking.transaction.model.TransactionType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private String transactionNumber;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionCategory category;
    private TransactionStatus status;
    private String description;
    private LocalDateTime createdAt;
}

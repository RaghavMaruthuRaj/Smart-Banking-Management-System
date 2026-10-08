package com.smartbanking.transaction.listener;

import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.transaction.event.TransactionCreatedEvent;
import com.smartbanking.transaction.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        Transaction tx = event.getTransaction();
        
        String details;
        String action;
        Long userId = null;

        switch (tx.getType()) {
            case DEPOSIT -> {
                action = "DEPOSIT_COMPLETED";
                userId = tx.getTargetAccount().getUser().getId();
                details = String.format("Deposit of $%s to Account %s. Status: %s", 
                        tx.getAmount(), tx.getTargetAccount().getAccountNumber(), tx.getStatus());
            }
            case WITHDRAWAL -> {
                action = "WITHDRAWAL_COMPLETED";
                userId = tx.getSourceAccount().getUser().getId();
                details = String.format("Withdrawal of $%s from Account %s. Status: %s", 
                        tx.getAmount(), tx.getSourceAccount().getAccountNumber(), tx.getStatus());
            }
            case TRANSFER -> {
                userId = tx.getSourceAccount().getUser().getId();
                if (tx.getStatus() == com.smartbanking.transaction.model.TransactionStatus.PENDING_FRAUD_HOLD) {
                    action = "TRANSFER_FRAUD_HOLD";
                    details = String.format("Transfer of $%s from Account %s to Account %s put on FRAUD HOLD.", 
                            tx.getAmount(), tx.getSourceAccount().getAccountNumber(), tx.getTargetAccount().getAccountNumber());
                } else {
                    action = "TRANSFER_COMPLETED";
                    details = String.format("Transfer of $%s from Account %s to Account %s. Category: %s", 
                            tx.getAmount(), tx.getSourceAccount().getAccountNumber(), tx.getTargetAccount().getAccountNumber(), tx.getCategory());
                }
            }
            default -> {
                action = "TRANSACTION_UNKNOWN";
                details = "Unknown transaction type: " + tx.getTransactionNumber();
            }
        }

        auditLogService.log(userId, action, details);
        log.info("Decoupled side-effects processed for transaction: {}. Action logged: {}", tx.getTransactionNumber(), action);
    }
}

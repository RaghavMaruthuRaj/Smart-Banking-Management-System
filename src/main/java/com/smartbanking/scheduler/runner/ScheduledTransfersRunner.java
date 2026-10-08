package com.smartbanking.scheduler.runner;

import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.scheduler.model.ScheduleFrequency;
import com.smartbanking.scheduler.model.ScheduleStatus;
import com.smartbanking.scheduler.model.ScheduledTransfer;
import com.smartbanking.scheduler.repository.ScheduledTransferRepository;
import com.smartbanking.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransfersRunner {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final TransactionService transactionService;
    private final AuditLogService auditLogService;

    // Run every 60 seconds to find due transfers
    @Scheduled(fixedDelay = 60000)
    public void executeDueTransfers() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTransfer> dueTransfers = scheduledTransferRepository
                .findByStatusAndScheduledDateBefore(ScheduleStatus.ACTIVE, now);

        if (dueTransfers.isEmpty()) {
            return;
        }

        log.info("Found {} scheduled transfers due for execution.", dueTransfers.size());

        for (ScheduledTransfer transfer : dueTransfers) {
            // Process each transfer in a try-catch to avoid breaking the entire loop
            try {
                processSingleTransfer(transfer);
            } catch (Exception e) {
                log.error("Failed to execute scheduled transfer ID {}: {}", transfer.getId(), e.getMessage());
                handleTransferFailure(transfer, e.getMessage());
            }
        }
    }

    @Transactional
    public void processSingleTransfer(ScheduledTransfer transfer) {
        log.info("Processing scheduled transfer ID {}: from {} to {} amount ${}", 
                transfer.getId(), 
                transfer.getSourceAccount().getAccountNumber(), 
                transfer.getTargetAccount().getAccountNumber(), 
                transfer.getAmount());

        // 1. Execute core transfer
        transactionService.transfer(
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getTargetAccount().getAccountNumber(),
                transfer.getAmount(),
                "Scheduled Transfer: " + transfer.getDescription(),
                com.smartbanking.transaction.model.TransactionCategory.TRANSFER
        );

        // 2. Advance schedule next date or complete it
        if (transfer.getFrequency() == ScheduleFrequency.ONCE) {
            transfer.setStatus(ScheduleStatus.COMPLETED);
        } else if (transfer.getFrequency() == ScheduleFrequency.WEEKLY) {
            transfer.setScheduledDate(transfer.getScheduledDate().plusWeeks(1));
        } else if (transfer.getFrequency() == ScheduleFrequency.MONTHLY) {
            transfer.setScheduledDate(transfer.getScheduledDate().plusMonths(1));
        }

        scheduledTransferRepository.save(transfer);
        
        auditLogService.log(
                transfer.getSourceAccount().getUser().getId(), 
                "SCHEDULED_TRANSFER_EXECUTED", 
                String.format("Scheduled transfer ID %d executed successfully. Next date: %s", 
                        transfer.getId(), transfer.getScheduledDate())
        );
    }

    private void handleTransferFailure(ScheduledTransfer transfer, String errorDetails) {
        try {
            // Pause the schedule so it doesn't loop forever
            transfer.setStatus(ScheduleStatus.PAUSED);
            scheduledTransferRepository.save(transfer);

            auditLogService.log(
                    transfer.getSourceAccount().getUser().getId(), 
                    "SCHEDULED_TRANSFER_FAILED", 
                    String.format("Scheduled transfer ID %d failed and has been paused. Reason: %s", 
                            transfer.getId(), errorDetails)
            );
        } catch (Exception e) {
            log.error("Failed to mark scheduled transfer ID {} as failed: {}", transfer.getId(), e.getMessage());
        }
    }
}

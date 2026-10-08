package com.smartbanking.scheduler.service;

import com.smartbanking.account.model.Account;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.common.exception.BankException;
import com.smartbanking.common.exception.ResourceNotFoundException;
import com.smartbanking.scheduler.dto.ScheduleRequest;
import com.smartbanking.scheduler.dto.ScheduleResponse;
import com.smartbanking.scheduler.model.ScheduleStatus;
import com.smartbanking.scheduler.model.ScheduledTransfer;
import com.smartbanking.scheduler.repository.ScheduledTransferRepository;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferService {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Account sourceAcc = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No banking account found for user: " + username));

        Account targetAcc = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.getTargetAccountNumber()));

        if (sourceAcc.getAccountNumber().equals(targetAcc.getAccountNumber())) {
            throw new BankException("Cannot schedule a transfer to the same account");
        }

        ScheduledTransfer schedule = ScheduledTransfer.builder()
                .sourceAccount(sourceAcc)
                .targetAccount(targetAcc)
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .scheduledDate(request.getScheduledDate())
                .status(ScheduleStatus.ACTIVE)
                .description(request.getDescription())
                .build();

        ScheduledTransfer saved = scheduledTransferRepository.save(schedule);

        auditLogService.log(user.getId(), "TRANSFER_SCHEDULE_CREATED", 
                String.format("Scheduled a %s transfer of $%s to %s on %s", 
                        request.getFrequency(), request.getAmount(), request.getTargetAccountNumber(), request.getScheduledDate()));

        log.info("Scheduled transfer ID {} created for account {}", saved.getId(), sourceAcc.getAccountNumber());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMySchedules(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No banking account found"));

        return scheduledTransferRepository.findBySourceAccountIdOrTargetAccountId(account.getId(), account.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelSchedule(Long scheduleId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledTransfer schedule = scheduledTransferRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled transfer not found"));

        // Verify ownership (only the source account user can cancel it)
        if (!schedule.getSourceAccount().getUser().getId().equals(user.getId())) {
            throw new BankException("Unauthorized: You do not own this scheduled transfer");
        }

        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BankException("Cannot cancel a completed schedule");
        }

        schedule.setStatus(ScheduleStatus.COMPLETED); // Or CANCELLED, using COMPLETED as a final status
        scheduledTransferRepository.save(schedule);

        auditLogService.log(user.getId(), "TRANSFER_SCHEDULE_CANCELLED", 
                String.format("Cancelled scheduled transfer ID %d", scheduleId));
        log.info("Cancelled scheduled transfer ID {}", scheduleId);
    }

    private ScheduleResponse mapToResponse(ScheduledTransfer st) {
        return ScheduleResponse.builder()
                .id(st.getId())
                .sourceAccountNumber(st.getSourceAccount().getAccountNumber())
                .targetAccountNumber(st.getTargetAccount().getAccountNumber())
                .amount(st.getAmount())
                .frequency(st.getFrequency())
                .scheduledDate(st.getScheduledDate())
                .status(st.getStatus())
                .description(st.getDescription())
                .createdAt(st.getCreatedAt())
                .build();
    }
}

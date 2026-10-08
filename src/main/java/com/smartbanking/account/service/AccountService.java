package com.smartbanking.account.service;

import com.smartbanking.account.dto.AccountResponse;
import com.smartbanking.account.model.Account;
import com.smartbanking.account.model.AccountStatus;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.common.exception.ResourceNotFoundException;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AccountResponse getAccountByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No account linked to user: " + username));
        return mapToAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountDetails(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return mapToAccountResponse(account);
    }

    @Transactional
    public void setAccountStatus(String accountNumber, AccountStatus status, User admin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(status);
        accountRepository.save(account);

        auditLogService.log(admin.getId(), "ACCOUNT_STATUS_CHANGE", 
                String.format("Account %s status updated from %s to %s by admin: %s", 
                        accountNumber, oldStatus, status, admin.getUsername()));
        
        log.info("Admin {} updated status of account {} to {}", admin.getUsername(), accountNumber, status);
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .tier(account.getTier())
                .status(account.getStatus())
                .build();
    }
}

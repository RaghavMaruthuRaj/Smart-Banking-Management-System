package com.smartbanking.transaction.service;

import com.smartbanking.account.model.Account;
import com.smartbanking.account.model.AccountStatus;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.common.exception.*;
import com.smartbanking.fraud.model.FraudFlag;
import com.smartbanking.fraud.repository.FraudFlagRepository;
import com.smartbanking.transaction.event.TransactionCreatedEvent;
import com.smartbanking.transaction.model.Transaction;
import com.smartbanking.transaction.model.TransactionCategory;
import com.smartbanking.transaction.model.TransactionStatus;
import com.smartbanking.transaction.model.TransactionType;
import com.smartbanking.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudFlagRepository fraudFlagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.banking.fraud.threshold-amount}")
    private BigDecimal fraudThresholdAmount;

    @Transactional
    public Transaction deposit(String accountNumber, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Deposit amount must be positive");
        }

        // 1. Pessimistic locking to lock the account balance row
        Account account = accountRepository.findByAccountNumberWithPessimisticLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Cannot deposit to a blocked account: " + accountNumber);
        }

        // 2. Adjust Balance
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        // 3. Create Transaction Record
        Transaction tx = Transaction.builder()
                .transactionNumber(UUID.randomUUID().toString())
                .targetAccount(account)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .category(TransactionCategory.OTHERS)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();

        Transaction savedTx = transactionRepository.save(tx);

        // 4. Publish Decoupled Event
        eventPublisher.publishEvent(new TransactionCreatedEvent(this, savedTx));
        log.info("Deposit successful: {} added to account {}", amount, accountNumber);

        return savedTx;
    }

    @Transactional
    public Transaction withdraw(String accountNumber, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Withdrawal amount must be positive");
        }

        // 1. Pessimistic lock
        Account account = accountRepository.findByAccountNumberWithPessimisticLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Cannot withdraw from a blocked account: " + accountNumber);
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds for withdrawal. Balance: " + account.getBalance());
        }

        // 2. Adjust Balance
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        // 3. Create Transaction
        Transaction tx = Transaction.builder()
                .transactionNumber(UUID.randomUUID().toString())
                .sourceAccount(account)
                .amount(amount)
                .type(TransactionType.WITHDRAWAL)
                .category(TransactionCategory.OTHERS)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();

        Transaction savedTx = transactionRepository.save(tx);

        // 4. Publish Event
        eventPublisher.publishEvent(new TransactionCreatedEvent(this, savedTx));
        log.info("Withdrawal successful: {} deducted from account {}", amount, accountNumber);

        return savedTx;
    }

    @Transactional
    public Transaction transfer(String sourceAccNumber, String targetAccNumber, BigDecimal amount, 
                                 String description, TransactionCategory category) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transfer amount must be positive");
        }
        if (sourceAccNumber.equals(targetAccNumber)) {
            throw new InvalidTransactionException("Cannot transfer funds to the same account");
        }

        // 1. Pre-fetch accounts to verify existences (without locking yet)
        Account sourceAcc = accountRepository.findByAccountNumber(sourceAccNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceAccNumber));
        Account targetAcc = accountRepository.findByAccountNumber(targetAccNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Target account not found: " + targetAccNumber));

        if (sourceAcc.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Source account is blocked: " + sourceAccNumber);
        }
        if (targetAcc.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Destination account is blocked: " + targetAccNumber);
        }

        // 2. Deterministic Lock Acquisition Order (avoid Deadlocks under concurrency!)
        Account lockedSource, lockedTarget;
        if (sourceAccNumber.compareTo(targetAccNumber) < 0) {
            lockedSource = accountRepository.findByAccountNumberWithPessimisticLock(sourceAccNumber).get();
            lockedTarget = accountRepository.findByAccountNumberWithPessimisticLock(targetAccNumber).get();
        } else {
            lockedTarget = accountRepository.findByAccountNumberWithPessimisticLock(targetAccNumber).get();
            lockedSource = accountRepository.findByAccountNumberWithPessimisticLock(sourceAccNumber).get();
        }

        if (lockedSource.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds. Transfer amount: " + amount + ", Balance: " + lockedSource.getBalance());
        }

        // 3. Fraud Engine Threshold Check
        boolean isSuspicious = amount.compareTo(fraudThresholdAmount) >= 0;

        Transaction tx = Transaction.builder()
                .transactionNumber(UUID.randomUUID().toString())
                .sourceAccount(lockedSource)
                .targetAccount(lockedTarget)
                .amount(amount)
                .type(TransactionType.TRANSFER)
                .category(category == null ? TransactionCategory.TRANSFER : category)
                .description(description)
                .build();

        if (isSuspicious) {
            // Flag as hold, DO NOT transfer money
            tx.setStatus(TransactionStatus.PENDING_FRAUD_HOLD);
            Transaction savedTx = transactionRepository.save(tx);

            FraudFlag flag = FraudFlag.builder()
                    .transaction(savedTx)
                    .reason("Transaction amount of $" + amount + " exceeds security threshold of $" + fraudThresholdAmount)
                    .build();
            fraudFlagRepository.save(flag);

            eventPublisher.publishEvent(new TransactionCreatedEvent(this, savedTx));
            log.warn("Transfer of {} from {} to {} blocked under suspected FRAUD threshold.", amount, sourceAccNumber, targetAccNumber);
            return savedTx;
        }

        // 4. Perform actual transfer (balances adjust)
        lockedSource.setBalance(lockedSource.getBalance().subtract(amount));
        lockedTarget.setBalance(lockedTarget.getBalance().add(amount));
        
        accountRepository.save(lockedSource);
        accountRepository.save(lockedTarget);

        tx.setStatus(TransactionStatus.COMPLETED);
        Transaction savedTx = transactionRepository.save(tx);

        eventPublisher.publishEvent(new TransactionCreatedEvent(this, savedTx));
        log.info("Transfer successful: {} transferred from {} to {}", amount, sourceAccNumber, targetAccNumber);

        return savedTx;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getAccountHistory(Long accountId, int page, int size) {
        return transactionRepository.findAllByAccountId(accountId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getUserHistory(Long userId, int page, int size) {
        return transactionRepository.findAllByUserId(userId, PageRequest.of(page, size));
    }
}

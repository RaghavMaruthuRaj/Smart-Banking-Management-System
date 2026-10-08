package com.smartbanking.fraud.service;

import com.smartbanking.account.model.Account;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.common.exception.AccountBlockedException;
import com.smartbanking.common.exception.BankException;
import com.smartbanking.common.exception.InsufficientFundsException;
import com.smartbanking.common.exception.ResourceNotFoundException;
import com.smartbanking.fraud.model.FraudFlag;
import com.smartbanking.fraud.repository.FraudFlagRepository;
import com.smartbanking.transaction.model.Transaction;
import com.smartbanking.transaction.model.TransactionStatus;
import com.smartbanking.transaction.repository.TransactionRepository;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudService {

    private final FraudFlagRepository fraudFlagRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<FraudFlag> getUnresolvedFlags() {
        return fraudFlagRepository.findByResolvedFalseOrderByFlaggedAtDesc();
    }

    @Transactional
    public void resolveFraudFlag(Long flagId, boolean approve, String notes, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        FraudFlag flag = fraudFlagRepository.findById(flagId)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud flag not found with ID: " + flagId));

        if (flag.isResolved()) {
            throw new BankException("Fraud flag has already been resolved");
        }

        Transaction tx = flag.getTransaction();

        if (approve) {
            // Override and complete the transaction. We must lock both accounts deterministically.
            String sourceAccNum = tx.getSourceAccount().getAccountNumber();
            String targetAccNum = tx.getTargetAccount().getAccountNumber();

            Account sourceAcc = accountRepository.findByAccountNumber(sourceAccNum).get();
            Account targetAcc = accountRepository.findByAccountNumber(targetAccNum).get();

            if (sourceAcc.getStatus() == com.smartbanking.account.model.AccountStatus.BLOCKED ||
                targetAcc.getStatus() == com.smartbanking.account.model.AccountStatus.BLOCKED) {
                throw new AccountBlockedException("Cannot override transaction; one of the accounts is blocked.");
            }

            Account lockedSource, lockedTarget;
            if (sourceAccNum.compareTo(targetAccNum) < 0) {
                lockedSource = accountRepository.findByAccountNumberWithPessimisticLock(sourceAccNum).get();
                lockedTarget = accountRepository.findByAccountNumberWithPessimisticLock(targetAccNum).get();
            } else {
                lockedTarget = accountRepository.findByAccountNumberWithPessimisticLock(targetAccNum).get();
                lockedSource = accountRepository.findByAccountNumberWithPessimisticLock(sourceAccNum).get();
            }

            if (lockedSource.getBalance().compareTo(tx.getAmount()) < 0) {
                throw new InsufficientFundsException("Insufficient funds. Available: " + lockedSource.getBalance() + ", Transfer amount: " + tx.getAmount());
            }

            // Transfer funds
            lockedSource.setBalance(lockedSource.getBalance().subtract(tx.getAmount()));
            lockedTarget.setBalance(lockedTarget.getBalance().add(tx.getAmount()));

            accountRepository.save(lockedSource);
            accountRepository.save(lockedTarget);

            tx.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(tx);

            flag.setResolved(true);
            flag.setResolvedBy(admin);
            flag.setResolvedAt(LocalDateTime.now());
            flag.setResolutionNotes(notes);
            fraudFlagRepository.save(flag);

            auditLogService.log(admin.getId(), "FRAUD_OVERRIDE_APPROVED", 
                    String.format("Transaction %s ($%s) approved by admin: %s. Notes: %s", 
                            tx.getTransactionNumber(), tx.getAmount(), admin.getUsername(), notes));
            log.info("Fraud override APPROVED for transaction {} by admin {}", tx.getTransactionNumber(), admin.getUsername());

        } else {
            // Reject and cancel the transaction
            tx.setStatus(TransactionStatus.CANCELLED);
            transactionRepository.save(tx);

            flag.setResolved(true);
            flag.setResolvedBy(admin);
            flag.setResolvedAt(LocalDateTime.now());
            flag.setResolutionNotes(notes);
            fraudFlagRepository.save(flag);

            auditLogService.log(admin.getId(), "FRAUD_OVERRIDE_REJECTED", 
                    String.format("Transaction %s ($%s) rejected/cancelled by admin: %s. Notes: %s", 
                            tx.getTransactionNumber(), tx.getAmount(), admin.getUsername(), notes));
            log.info("Fraud override REJECTED for transaction {} by admin {}", tx.getTransactionNumber(), admin.getUsername());
        }
    }
}

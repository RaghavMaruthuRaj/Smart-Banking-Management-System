package com.smartbanking.transaction.controller;

import com.smartbanking.account.dto.AccountResponse;
import com.smartbanking.account.service.AccountService;
import com.smartbanking.transaction.dto.TransactionRequest;
import com.smartbanking.transaction.dto.TransactionResponse;
import com.smartbanking.transaction.dto.TransferRequest;
import com.smartbanking.transaction.model.Transaction;
import com.smartbanking.transaction.service.TransactionService;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import com.smartbanking.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final UserRepository userRepository;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody TransactionRequest request, 
                                                        Authentication authentication) {
        AccountResponse myAcc = accountService.getAccountByUsername(authentication.getName());
        Transaction tx = transactionService.deposit(myAcc.getAccountNumber(), request.getAmount(), request.getDescription());
        return ResponseEntity.ok(mapToResponse(tx));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody TransactionRequest request, 
                                                         Authentication authentication) {
        AccountResponse myAcc = accountService.getAccountByUsername(authentication.getName());
        Transaction tx = transactionService.withdraw(myAcc.getAccountNumber(), request.getAmount(), request.getDescription());
        return ResponseEntity.ok(mapToResponse(tx));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request, 
                                                         Authentication authentication) {
        AccountResponse myAcc = accountService.getAccountByUsername(authentication.getName());
        Transaction tx = transactionService.transfer(
                myAcc.getAccountNumber(),
                request.getTargetAccountNumber(),
                request.getAmount(),
                request.getDescription(),
                request.getCategory()
        );
        return ResponseEntity.ok(mapToResponse(tx));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Page<Transaction> transactions = transactionService.getUserHistory(user.getId(), page, size);
        Page<TransactionResponse> responses = transactions.map(this::mapToResponse);
        return ResponseEntity.ok(responses);
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder()
                .transactionNumber(tx.getTransactionNumber())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .targetAccountNumber(tx.getTargetAccount() != null ? tx.getTargetAccount().getAccountNumber() : null)
                .amount(tx.getAmount())
                .type(tx.getType())
                .category(tx.getCategory())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}

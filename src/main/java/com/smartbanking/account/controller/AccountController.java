package com.smartbanking.account.controller;

import com.smartbanking.account.dto.AccountResponse;
import com.smartbanking.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/my")
    public ResponseEntity<AccountResponse> getMyAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        AccountResponse response = accountService.getAccountByUsername(authentication.getName());
        return ResponseEntity.ok(response);
    }
}

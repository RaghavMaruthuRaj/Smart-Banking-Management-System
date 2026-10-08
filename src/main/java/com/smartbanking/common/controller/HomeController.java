package com.smartbanking.common.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "Smart Banking Management System");
        response.put("message", "Backend API is running successfully!");
        response.put("h2Console", "http://localhost:8080/h2-console");
        
        Map<String, String> publicEndpoints = new LinkedHashMap<>();
        publicEndpoints.put("Register User", "POST /api/auth/register");
        publicEndpoints.put("Login User", "POST /api/auth/login");
        publicEndpoints.put("Refresh Token", "POST /api/auth/refresh");
        publicEndpoints.put("Logout", "POST /api/auth/logout");
        publicEndpoints.put("H2 Database Console", "GET /h2-console");
        response.put("publicEndpoints", publicEndpoints);

        Map<String, String> protectedEndpoints = new LinkedHashMap<>();
        protectedEndpoints.put("Deposit Funds", "POST /api/transactions/deposit");
        protectedEndpoints.put("Withdraw Funds", "POST /api/transactions/withdraw");
        protectedEndpoints.put("Transfer Funds", "POST /api/transactions/transfer");
        protectedEndpoints.put("Account Details", "GET /api/accounts/my-account");
        protectedEndpoints.put("Transaction History", "GET /api/transactions/history");
        protectedEndpoints.put("Schedule Transfer", "POST /api/scheduler/transfer");
        response.put("protectedEndpoints", protectedEndpoints);

        return ResponseEntity.ok(response);
    }
}

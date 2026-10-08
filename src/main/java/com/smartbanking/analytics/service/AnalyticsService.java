package com.smartbanking.analytics.service;

import com.smartbanking.account.model.Account;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.common.exception.ResourceNotFoundException;
import com.smartbanking.transaction.model.TransactionCategory;
import com.smartbanking.transaction.repository.TransactionRepository;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getSpendingByCategory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No banking account found for user: " + username));

        List<Object[]> rawResults = transactionRepository.getSpendingByCategory(account.getId());
        Map<String, BigDecimal> report = new HashMap<>();

        // Initialize all categories to ZERO to make the visual chart presentation clean
        for (TransactionCategory cat : TransactionCategory.values()) {
            if (cat != TransactionCategory.TRANSFER) { // Focus on specific categories
                report.put(cat.name(), BigDecimal.ZERO);
            }
        }
        report.put("GENERAL_TRANSFER", BigDecimal.ZERO);

        for (Object[] row : rawResults) {
            TransactionCategory category = (TransactionCategory) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            
            if (category == TransactionCategory.TRANSFER) {
                report.put("GENERAL_TRANSFER", sum);
            } else {
                report.put(category.name(), sum);
            }
        }

        return report;
    }
}

package com.smartbanking.account.dto;

import com.smartbanking.account.model.AccountStatus;
import com.smartbanking.account.model.AccountTier;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private AccountTier tier;
    private AccountStatus status;
}

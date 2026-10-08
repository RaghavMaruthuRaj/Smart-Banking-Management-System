package com.smartbanking.scheduler.dto;

import com.smartbanking.scheduler.model.ScheduleFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ScheduleRequest {

    @NotBlank(message = "Destination account number is required")
    private String targetAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Frequency is required")
    private ScheduleFrequency frequency;

    @NotNull(message = "Schedule run date is required")
    @Future(message = "Schedule run date must be in the future")
    private LocalDateTime scheduledDate;

    private String description;
}

package com.smartbanking.scheduler.dto;

import com.smartbanking.scheduler.model.ScheduleFrequency;
import com.smartbanking.scheduler.model.ScheduleStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ScheduleResponse {
    private Long id;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private ScheduleFrequency frequency;
    private LocalDateTime scheduledDate;
    private ScheduleStatus status;
    private String description;
    private LocalDateTime createdAt;
}

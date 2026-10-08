package com.smartbanking.scheduler.controller;

import com.smartbanking.scheduler.dto.ScheduleRequest;
import com.smartbanking.scheduler.dto.ScheduleResponse;
import com.smartbanking.scheduler.service.ScheduledTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/scheduler")
@RequiredArgsConstructor
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    @PostMapping("/transfer")
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleRequest request, 
                                                           Authentication authentication) {
        ScheduleResponse response = scheduledTransferService.createSchedule(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ScheduleResponse>> getMySchedules(Authentication authentication) {
        List<ScheduleResponse> responses = scheduledTransferService.getMySchedules(authentication.getName());
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelSchedule(@PathVariable Long id, Authentication authentication) {
        scheduledTransferService.cancelSchedule(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

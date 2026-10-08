package com.smartbanking.scheduler.repository;

import com.smartbanking.scheduler.model.ScheduleStatus;
import com.smartbanking.scheduler.model.ScheduledTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {
    List<ScheduledTransfer> findByStatusAndScheduledDateBefore(ScheduleStatus status, LocalDateTime dateTime);
    List<ScheduledTransfer> findBySourceAccountIdOrTargetAccountId(Long sourceAccountId, Long targetAccountId);
}

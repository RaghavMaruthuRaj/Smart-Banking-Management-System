package com.smartbanking.fraud.model;

import com.smartbanking.transaction.model.Transaction;
import com.smartbanking.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String reason; // e.g. "Amount exceeds $10,000" or "Suspicious transfer activity"

    @Column(name = "flagged_at", nullable = false)
    private LocalDateTime flaggedAt;

    @Column(nullable = false)
    private boolean resolved;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @PrePersist
    protected void onCreate() {
        this.flaggedAt = LocalDateTime.now();
        this.resolved = false;
    }
}

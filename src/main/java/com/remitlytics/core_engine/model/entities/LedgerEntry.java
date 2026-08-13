package com.remitlytics.core_engine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "entry_type", nullable = false, length = 10)
    private String entryType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
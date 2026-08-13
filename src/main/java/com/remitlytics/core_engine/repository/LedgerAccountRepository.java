package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByTenantIdAndAccountType(UUID tenantId, String accountType);
}
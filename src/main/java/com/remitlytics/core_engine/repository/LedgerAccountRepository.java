package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByTenantIdAndName(UUID tenantId, String name);
}
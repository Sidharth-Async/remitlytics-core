package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("SELECT e FROM LedgerEntry e JOIN FETCH e.account a WHERE a.tenant.id = :tenantId")
    List<LedgerEntry> findAllByTenantIdWithAccount(@Param("tenantId") UUID tenantId);
}
package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {}
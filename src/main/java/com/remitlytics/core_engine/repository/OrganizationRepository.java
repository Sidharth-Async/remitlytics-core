package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}

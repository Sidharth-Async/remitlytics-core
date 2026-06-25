package com.remitlytics.core_engine.repository;

import com.remitlytics.core_engine.model.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
}

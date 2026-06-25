package com.remitlytics.core_engine.config;

import com.remitlytics.core_engine.model.entities.Client;
import com.remitlytics.core_engine.model.entities.Organization;
import com.remitlytics.core_engine.repository.ClientRepository;
import com.remitlytics.core_engine.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only seed data if the database is currently empty
        if (organizationRepository.count() == 0) {
            log.info("🚀 Seeding master data into PostgreSQL...");

            // 1. Seed Organization
            Organization org = new Organization();
            org.setName("Acme Corporate Solutions");
            org.setStripeAccountId("acct_12345fake");
            Organization savedOrg = organizationRepository.save(org);

            // 2. Seed Client linked to that Organization
            Client client = new Client();
            client.setName("John Doe Logistics");
            client.setEmail("billing@johndoelogistics.com");
            client.setOrganization(savedOrg);
            Client savedClient = clientRepository.save(client);

            log.info("✅ Seeding complete!");
            log.info("👉 USE THIS CLIENT UUID IN BRUNO: {}", savedClient.getId());
        }
    }
}
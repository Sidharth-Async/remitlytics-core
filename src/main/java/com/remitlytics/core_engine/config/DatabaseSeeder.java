package com.remitlytics.core_engine.config;

import com.remitlytics.core_engine.model.entities.ApiKey;
import com.remitlytics.core_engine.model.entities.Client;
import com.remitlytics.core_engine.model.entities.Organization;
import com.remitlytics.core_engine.model.entities.Tenant;
import com.remitlytics.core_engine.repository.ApiKeyRepository;
import com.remitlytics.core_engine.repository.ClientRepository;
import com.remitlytics.core_engine.repository.OrganizationRepository;
import com.remitlytics.core_engine.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only seed data if the database is currently empty
        if (tenantRepository.count() == 0) {
            log.info("🚀 Seeding master data into PostgreSQL...");

            // 1. Seed Tenant
            Tenant tenant = new Tenant();
            tenant.setCompanyName("Remitlytics Local HQ");
            Tenant savedTenant = tenantRepository.save(tenant);

            // 2. Seed API Key (Hash it before saving!)
            String rawApiKey = "remitlytics_local_dev_key";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));

            ApiKey apiKey = new ApiKey();
            apiKey.setTenant(savedTenant);
            apiKey.setKeyHash(HexFormat.of().formatHex(hashBytes));
            apiKey.setActive(true);
            apiKeyRepository.save(apiKey);

            // 3. Seed Organization
            Organization org = new Organization();
            org.setName("Acme Corporate Solutions");
            org.setStripeAccountId("acct_12345fake");
            Organization savedOrg = organizationRepository.save(org);

            // 4. Seed Client linked to that Organization
            Client client = new Client();
            client.setName("John Doe Logistics");
            client.setEmail("billing@johndoelogistics.com");
            client.setOrganization(savedOrg);
            Client savedClient = clientRepository.save(client);

            log.info("✅ Seeding complete!");
            log.info("👉 USE THIS RAW API KEY IN BRUNO: {}", rawApiKey);
            log.info("👉 USE THIS CLIENT UUID IN BRUNO: {}", savedClient.getId());
        }
    }
}
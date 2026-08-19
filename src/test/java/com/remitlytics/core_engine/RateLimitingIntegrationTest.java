package com.remitlytics.core_engine;

import com.remitlytics.core_engine.model.entities.ApiKey;
import com.remitlytics.core_engine.model.entities.Tenant;
import com.remitlytics.core_engine.repository.ApiKeyRepository;
import com.remitlytics.core_engine.repository.TenantRepository;
import com.remitlytics.core_engine.security.RateLimiterService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remitlytics_ratelimit_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "remitlytics.ratelimit.capacity=3",
        "remitlytics.ratelimit.refill-tokens=3",
        "remitlytics.ratelimit.refill-duration-seconds=60"
})
class RateLimitingIntegrationTest {

    @MockBean
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    private static final String TENANT_A_KEY = "remit_live_rate_tenant_a_123";
    private static final String TENANT_B_KEY = "remit_live_rate_tenant_b_456";

    @BeforeEach
    void setUp() throws Exception {
        rateLimiterService.clearCache();
        apiKeyRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant A
        Tenant tenantA = new Tenant();
        tenantA.setCompanyName("Rate Limit Corp A");
        tenantA = tenantRepository.save(tenantA);

        ApiKey apiKeyA = new ApiKey();
        apiKeyA.setTenant(tenantA);
        apiKeyA.setKeyHash(hashKey(TENANT_A_KEY));
        apiKeyA.setActive(true);
        apiKeyRepository.save(apiKeyA);

        // 2. Seed Tenant B
        Tenant tenantB = new Tenant();
        tenantB.setCompanyName("Rate Limit Corp B");
        tenantB = tenantRepository.save(tenantB);

        ApiKey apiKeyB = new ApiKey();
        apiKeyB.setTenant(tenantB);
        apiKeyB.setKeyHash(hashKey(TENANT_B_KEY));
        apiKeyB.setActive(true);
        apiKeyRepository.save(apiKeyB);
    }

    @Test
    @DisplayName("Tenant A should exhaust capacity of 3 requests and receive 429, while Tenant B remains unaffected")
    void shouldEnforceRateLimitPerTenantKey() throws Exception {

        // --- Exhaust Tenant A's 3 token quota ---
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(get("/api/v1/invoices")
                            .header("X-API-KEY", TENANT_A_KEY))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }

        // 4th request from Tenant A -> MUST return 429 TOO_MANY_REQUESTS
        mockMvc.perform(get("/api/v1/invoices")
                        .header("X-API-KEY", TENANT_A_KEY))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-RateLimit-Retry-After-Seconds"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));

        // --- Tenant B should STILL have their quota available ---
        mockMvc.perform(get("/api/v1/invoices")
                        .header("X-API-KEY", TENANT_B_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "2"));
    }

    private String hashKey(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
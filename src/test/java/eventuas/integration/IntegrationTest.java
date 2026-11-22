package eventuas.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestSecurityConfig.class)
public abstract class IntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	static {
		postgres.start();
	}

	@Autowired
	protected MockMvc mvc;

	protected TestHarness harness;

	@BeforeEach
	void setUpHarness() {
		this.harness = new TestHarness(mvc);
	}

	@TestConfiguration
	static class TestSecurityConfig {
		@Bean
		JwtDecoder jwtDecoder() {
			return token -> Jwt.withTokenValue(token)
				.header("alg", "none")
				.claim("sub", UUID.randomUUID().toString())
				.build();
		}
	}
}
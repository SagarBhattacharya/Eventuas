package eventuas;

import eventuas.integration.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHardeningTest extends IntegrationTest {

	@Test
	void permitsActuatorHealthAndBlocksUnexposedEndpoints() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"));

		mvc.perform(get("/actuator/env"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void enforcesConfiguredCorsOriginsOnPreflight() throws Exception {
		mvc.perform(options("/events/published")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

		mvc.perform(options("/events/published")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

	@Test
	void propagatesOrGeneratesRequestIdHeader() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(header().exists("X-Request-Id"));

		mvc.perform(get("/actuator/health")
				.header("X-Request-Id", "req-trace-123"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Request-Id", "req-trace-123"));
	}
}
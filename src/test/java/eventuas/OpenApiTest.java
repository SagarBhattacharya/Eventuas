package eventuas;

import eventuas.integration.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiTest extends IntegrationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void exposesCompleteAndCleanV1Contract() throws Exception {
		String body = mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		JsonNode paths = root.get("paths");

		assertThat(paths.propertyNames()).containsExactlyInAnyOrder(
			"/users/me",
			"/events",
			"/events/published",
			"/events/published/{eventId}",
			"/events/{eventId}/publish",
			"/events/{eventId}/close",
			"/events/{eventId}/stats",
			"/events/{eventId}/tiers",
			"/events/{eventId}/tiers/{tierId}/close",
			"/events/published/{eventId}/tiers",
			"/tiers/{tierId}/buy",
			"/tickets/me",
			"/tickets/{ticketId}/qr",
			"/events/{eventId}/staff",
			"/events/{eventId}/staff/{staffUserId}",
			"/check-ins"
		);

		assertThat(root.path("components")
			.path("schemas")
			.has("PublicEventDetail"))
			.isTrue();
		assertThat(root.path("components")
			.path("schemas")
			.has("EventStats"))
			.isTrue();

		assertThat(body).doesNotContain("\"Jwt\"");
	}
}
package eventuas.integration;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestHarness {

	private final MockMvc mvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public TestHarness(MockMvc mvc) {
		this.mvc = mvc;
	}

	public String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

	public ResultActions postJson(String uri, TestUser actor, Object body, Object... uriVars) throws Exception {
		return mvc.perform(post(uri, uriVars)
			.with(actor.jwtPostProcessor())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(body)));
	}

	public void provision(TestUser user) throws Exception {
		mvc.perform(get("/users/me")
				.with(user.jwtPostProcessor()))
			.andExpect(status().isOk());
	}

	public String createDraftEvent(TestUser organizer, String name) throws Exception {
		Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
		String response = postJson("/events", organizer, Map.of(
			"name", name,
			"description", "Test event description",
			"venue", "Main Auditorium",
			"startDate", start.toString(),
			"endDate", start.plus(2, ChronoUnit.HOURS).toString()
		))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		return objectMapper.readTree(response).get("id").asString();
	}

	public String createPublishedEvent(TestUser organizer, String name) throws Exception {
		String eventId = createDraftEvent(organizer, name);
		publishEvent(organizer, eventId);
		return eventId;
	}

	public void publishEvent(TestUser organizer, String eventId) throws Exception {
		mvc.perform(post("/events/{id}/publish", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk());
	}

	public void closeEvent(TestUser organizer, String eventId) throws Exception {
		mvc.perform(post("/events/{id}/close", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk());
	}

	public String createTier(TestUser organizer, String eventId, String tier, long price, long qty) throws Exception {
		String response = postJson("/events/{eventId}/tiers", organizer, Map.of(
			"tier", tier,
			"description", tier + " access",
			"availableQty", qty,
			"price", price,
			"currency", "INR"
		), eventId)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		return objectMapper.readTree(response).get("id").asString();
	}

	public void closeTier(TestUser organizer, String eventId, String tierId) throws Exception {
		mvc.perform(post("/events/{eventId}/tiers/{tierId}/close", eventId, tierId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk());
	}

	public void assignStaff(TestUser organizer, String eventId, String email) throws Exception {
		postJson("/events/{eventId}/staff", organizer, Map.of("email", email), eventId)
			.andExpect(status().isCreated());
	}

	public String buyTicket(TestUser buyer, String tierId) throws Exception {
		String response = mvc.perform(post("/tiers/{tierId}/buy", tierId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		return objectMapper.readTree(response).get("id").asString();
	}

	public String fetchQrToken(TestUser buyer, String ticketId) throws Exception {
		String response = mvc.perform(get("/tickets/{ticketId}/qr", ticketId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		return objectMapper.readTree(response).get("qrToken").asString();
	}
}
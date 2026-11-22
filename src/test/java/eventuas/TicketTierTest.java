package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketTierTest extends IntegrationTest {

	@Test
	void organizerCreatesTicketTierForOwnedEvent() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Rust Systems Conf");

		harness.postJson("/events/{eventId}/tiers", organizer, Map.of(
				"tier", "General Admission",
				"description", "Standard conference pass",
				"availableQty", 100,
				"price", 1500,
				"currency", "INR"
			), eventId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.eventId").value(eventId))
			.andExpect(jsonPath("$.tier").value("General Admission"))
			.andExpect(jsonPath("$.availableQty").value(100))
			.andExpect(jsonPath("$.price").value(1500))
			.andExpect(jsonPath("$.currency").value("INR"))
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void rejectsDuplicateActiveTierNameOnSameEvent() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Duplicate Tier Conf");

		harness.createTier(organizer, eventId, "VIP", 5000, 20);

		harness.postJson("/events/{eventId}/tiers", organizer, Map.of(
				"tier", "VIP",
				"description", "Duplicate VIP tier",
				"availableQty", 10,
				"price", 6000,
				"currency", "INR"
			), eventId)
			.andExpect(status().isConflict());
	}

	@Test
	void allowsReplacingTierPriceAfterClosingPreviousTier() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Dynamic Pricing Summit");

		String oldTierId = harness.createTier(organizer, eventId, "Early Bird", 1000, 50);

		mvc.perform(post("/events/{eventId}/tiers/{tierId}/close", eventId, oldTierId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"));

		String newTierId = harness.createTier(organizer, eventId, "Early Bird", 1500, 30);

		mvc.perform(get("/events/{eventId}/tiers", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", hasItem(oldTierId)))
			.andExpect(jsonPath("$[*].id", hasItem(newTierId)));
	}

	@Test
	void publicEndpointShowsOnlyActiveTiersForPublishedEvents() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Public Ticketed Event");

		String activeTierId = harness.createTier(organizer, eventId, "Standard", 2000, 40);
		String closedTierId = harness.createTier(organizer, eventId, "Promo", 500, 10);

		harness.closeTier(organizer, eventId, closedTierId);

		mvc.perform(get("/events/published/{eventId}/tiers", eventId))
			.andExpect(status().isNotFound());

		harness.publishEvent(organizer, eventId);

		mvc.perform(get("/events/published/{eventId}/tiers", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", hasItem(activeTierId)))
			.andExpect(jsonPath("$[*].id", not(hasItem(closedTierId))));
	}

	@Test
	void forbidsNonOrganizerFromManagingTiers() throws Exception {
		var organizer = TestUser.random("Organizer");
		var intruder = TestUser.random("Intruder");
		String eventId = harness.createDraftEvent(organizer, "Protected Event");

		harness.postJson("/events/{eventId}/tiers", intruder, Map.of(
				"tier", "VIP",
				"description", "Unauthorized tier",
				"availableQty", 10,
				"price", 999,
				"currency", "INR"
			), eventId)
			.andExpect(status().isForbidden());
	}
}
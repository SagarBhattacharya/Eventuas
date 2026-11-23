package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventTest extends IntegrationTest {

	@Test
	void createsDraftEventForAuthenticatedOrganizer() throws Exception {
		var organizer = TestUser.random("Organizer");
		Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
		Instant end = start.plus(3, ChronoUnit.HOURS);

		harness.postJson("/events", organizer, Map.of(
				"name", "Systems Engineering Meetup",
				"description", "Deep dive into storage engines",
				"venue", "Kolkata Tech Hub",
				"startDate", start.toString(),
				"endDate", end.toString()
			))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Systems Engineering Meetup"))
			.andExpect(jsonPath("$.venue").value("Kolkata Tech Hub"))
			.andExpect(jsonPath("$.organizerId").value(organizer.id().toString()))
			.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	void rejectsEventWhenEndDateIsNotAfterStartDate() throws Exception {
		var organizer = TestUser.random("Organizer");
		Instant start = Instant.now().plus(7, ChronoUnit.DAYS);

		harness.postJson("/events", organizer, Map.of(
				"name", "Invalid Window Event",
				"description", "Deep dive into storage engines",
				"venue", "Kolkata",
				"startDate", start.toString(),
				"endDate", start.minus(1, ChronoUnit.HOURS).toString()
			))
			.andExpect(status().isBadRequest());
	}

	@Test
	void closedEventCannotBeRepublished() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "One-Way Lifecycle");

		harness.closeEvent(organizer, eventId);

		mvc.perform(post("/events/{id}/publish", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isConflict());
	}

	@Test
	void forbidsNonOrganizerFromChangingEventState() throws Exception {
		var organizer = TestUser.random("Organizer");
		var intruder = TestUser.random("Intruder");
		String eventId = harness.createDraftEvent(organizer, "Protected Event");

		mvc.perform(post("/events/{id}/publish", eventId)
				.with(intruder.jwtPostProcessor()))
			.andExpect(status().isForbidden());
	}

	@Test
	void closingEventCascadesClosureToAllActiveTiers() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createPublishedEvent(organizer, "Cascade Closure Conf");
		String tierA = harness.createTier(organizer, eventId, "General", 1000, 20);
		String tierB = harness.createTier(organizer, eventId, "VIP", 3000, 10);

		harness.closeEvent(organizer, eventId);

		mvc.perform(get("/events/{eventId}/tiers", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + tierA + "')].status").value("CLOSED"))
			.andExpect(jsonPath("$[?(@.id == '" + tierB + "')].status").value("CLOSED"));
	}

	@Test
	void returnsPublishedEventDetailWithOnlyActiveTiersAndHidesDrafts() throws Exception {
		var organizer = TestUser.random("Organizer");
		String draftId = harness.createDraftEvent(organizer, "Hidden Draft");

		// Draft events are invisible publicly
		mvc.perform(get("/events/published/{eventId}", draftId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		String eventId = harness.createDraftEvent(organizer, "Distributed Systems Conf");
		String activeTierId = harness.createTier(organizer, eventId, "Early Bird", 1500, 50);
		String closedTierId = harness.createTier(organizer, eventId, "VIP", 5000, 10);
		harness.publishEvent(organizer, eventId);

		mvc.perform(post("/events/{eventId}/tiers/{tierId}/close", eventId, closedTierId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk());

		// Active event returns full details + only ACTIVE tiers
		mvc.perform(get("/events/published/{eventId}", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(eventId))
			.andExpect(jsonPath("$.name").value("Distributed Systems Conf"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.tiers", hasSize(1)))
			.andExpect(jsonPath("$.tiers[0].id").value(activeTierId))
			.andExpect(jsonPath("$.tiers[0].tier").value("Early Bird"))
			.andExpect(jsonPath("$.tiers[0].status").value("ACTIVE"));
	}

	@Test
	void returnsClosedEventDetailWithEmptyActiveTiers() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createPublishedEvent(organizer, "Concluded Summit");
		harness.createTier(organizer, eventId, "General", 1000, 25);

		harness.closeEvent(organizer, eventId);

		mvc.perform(get("/events/published/{eventId}", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(eventId))
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.tiers", hasSize(0)));
	}

	@Test
	void returnsEventStatsWithTierBreakdownForOrganizer() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");
		String eventId = harness.createDraftEvent(organizer, "Kernel Internals Summit");
		String gaTierId = harness.createTier(organizer, eventId, "GA", 1000, 10);
		String vipTierId = harness.createTier(organizer, eventId, "VIP", 2500, 5);
		harness.publishEvent(organizer, eventId);

		// Buy 2 GA tickets and check in 1 of them
		String ticket1 = harness.buyTicket(buyer, gaTierId);
		var _ = harness.buyTicket(buyer, gaTierId);
		String qrToken = harness.fetchQrToken(buyer, ticket1);
		var _ = harness.checkIn(organizer, qrToken);

		mvc.perform(get("/events/{eventId}/stats", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventId").value(eventId))
			.andExpect(jsonPath("$.totalCapacity").value(15))
			.andExpect(jsonPath("$.ticketsSold").value(2))
			.andExpect(jsonPath("$.ticketsRemaining").value(13))
			.andExpect(jsonPath("$.checkedInCount").value(1))
			.andExpect(jsonPath("$.tiers", hasSize(2)))
			.andExpect(jsonPath("$.tiers[0].tierId").value(gaTierId))
			.andExpect(jsonPath("$.tiers[0].soldCount").value(2))
			.andExpect(jsonPath("$.tiers[0].remainingQty").value(8))
			.andExpect(jsonPath("$.tiers[0].checkedInCount").value(1))
			.andExpect(jsonPath("$.tiers[0].revenue").value(2000))
			.andExpect(jsonPath("$.tiers[1].tierId").value(vipTierId))
			.andExpect(jsonPath("$.tiers[1].soldCount").value(0))
			.andExpect(jsonPath("$.tiers[1].remainingQty").value(5))
			.andExpect(jsonPath("$.tiers[1].revenue").value(0));
	}

	@Test
	void forbidsNonOrganizerFromViewingEventStats() throws Exception {
		var organizer = TestUser.random("Organizer");
		var outsider = TestUser.random("Outsider");
		String eventId = harness.createPublishedEvent(organizer, "Private Metrics Conf");

		mvc.perform(get("/events/{eventId}/stats", eventId)
				.with(outsider.jwtPostProcessor()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_AUTHORIZED"));
	}

	@Test
	void rejectsPublishingEventWithNoActiveTiers() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Empty Tier Event");

		// Draft with 0 tiers -> 409 Conflict
		mvc.perform(post("/events/{id}/publish", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVENT_HAS_NO_ACTIVE_TIERS"));

		// Draft with only CLOSED tiers -> still 409 Conflict
		String tierId = harness.createTier(organizer, eventId, "Early Bird", 1000, 10);
		harness.closeTier(organizer, eventId, tierId);

		mvc.perform(post("/events/{id}/publish", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVENT_HAS_NO_ACTIVE_TIERS"));

		// Adding an active tier allows publishing
		harness.createTier(organizer, eventId, "General", 1500, 20);
		mvc.perform(post("/events/{id}/publish", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void listsPublishedAndClosedEventsPubliclyButNeverDrafts() throws Exception {
		var organizer = TestUser.random("Organizer");
		String draftId = harness.createDraftEvent(organizer, "Secret Draft");
		String activeId = harness.createDraftEvent(organizer, "Live Rust Conf");
		String closedId = harness.createDraftEvent(organizer, "Past Kernel Summit");

		harness.createTier(organizer, activeId, "General", 1000, 10);
		harness.createTier(organizer, closedId, "Closeable General", 1000, 10);
		mvc.perform(post("/events/{id}/publish", activeId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		harness.publishEvent(organizer, closedId);

		mvc.perform(post("/events/{id}/close", closedId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"));

		mvc.perform(get("/events/published"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(activeId)))
			.andExpect(jsonPath("$.items[*].id", hasItem(closedId)))
			.andExpect(jsonPath("$.items[*].id", not(hasItem(draftId))));
	}

	@Test
	void searchesFiltersAndPaginatesPublishedEvents() throws Exception {
		var organizer = TestUser.random("Organizer");
		String tag = UUID.randomUUID().toString().substring(0, 8);

		String draftId = harness.createDraftEvent(organizer, "Rust Draft " + tag);
		String event1 = harness.createPublishedEvent(organizer, "Rust Systems Alpha " + tag);
		String event2 = harness.createPublishedEvent(organizer, "Rust Storage Beta " + tag);
		String event3 = harness.createPublishedEvent(organizer, "Rust Kernel Gamma " + tag);
		String closedEvent = harness.createPublishedEvent(organizer, "Rust Archived " + tag);
		harness.closeEvent(organizer, closedEvent);

		// 1. Page 0 with size=2 scoped by unique search tag
		mvc.perform(get("/events/published")
				.param("q", tag)
				.param("page", "0")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.items[*].id", not(hasItem(draftId))));

		// 2. Page 1 returns remaining 2 items and sets hasNext=false
		mvc.perform(get("/events/published")
				.param("q", tag)
				.param("page", "1")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));

		// 3. Status filter isolates ACTIVE vs CLOSED and never exposes DRAFT
		mvc.perform(get("/events/published")
				.param("q", tag)
				.param("status", "ACTIVE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(event1, event2, event3)));

		mvc.perform(get("/events/published")
				.param("q", tag)
				.param("status", "DRAFT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void listsOnlyOwnedEventsForAuthenticatedOrganizer() throws Exception {
		var organizerA = TestUser.random("Alice");
		var organizerB = TestUser.random("Bob");

		String eventA = harness.createDraftEvent(organizerA, "Organizer A Draft");
		String eventB = harness.createDraftEvent(organizerB, "Organizer B Draft");

		mvc.perform(get("/events"))
			.andExpect(status().isUnauthorized());

		mvc.perform(get("/events")
				.with(organizerA.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(eventA)))
			.andExpect(jsonPath("$.items[*].id", not(hasItem(eventB))));
	}

	@Test
	void searchesFiltersAndPaginatesOrganizerEvents() throws Exception {
		var organizer = TestUser.random("Organizer");
		var otherOrganizer = TestUser.random("Other");

		String draftId = harness.createDraftEvent(organizer, "Kernel Workshop Draft");
		String activeId1 = harness.createPublishedEvent(organizer, "Kernel Summit Alpha");
		String activeId2 = harness.createPublishedEvent(organizer, "Kernel Summit Beta");
		harness.createPublishedEvent(otherOrganizer, "Kernel Summit Outsider");

		// 1. Filter by status=ACTIVE and search keyword, paginated with size=1
		mvc.perform(get("/events")
				.with(organizer.jwtPostProcessor())
				.param("q", "Kernel")
				.param("status", "ACTIVE")
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));

		// 2. Filter by status=DRAFT returns only organizer's draft
		mvc.perform(get("/events")
				.with(organizer.jwtPostProcessor())
				.param("status", "DRAFT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].id").value(draftId))
			.andExpect(jsonPath("$.items[*].id", not(hasItem(activeId1))))
			.andExpect(jsonPath("$.items[*].id", not(hasItem(activeId2))));
	}
}
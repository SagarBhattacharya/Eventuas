package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
			.andExpect(jsonPath("$[*].id", hasItem(eventA)))
			.andExpect(jsonPath("$[*].id", not(hasItem(eventB))));
	}

	@Test
	void listsPublishedAndClosedEventsPubliclyButNeverDrafts() throws Exception {
		var organizer = TestUser.random("Organizer");
		String draftId = harness.createDraftEvent(organizer, "Secret Draft");
		String activeId = harness.createDraftEvent(organizer, "Live Rust Conf");
		String closedId = harness.createDraftEvent(organizer, "Past Kernel Summit");

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
			.andExpect(jsonPath("$[*].id", hasItem(activeId)))
			.andExpect(jsonPath("$[*].id", hasItem(closedId)))
			.andExpect(jsonPath("$[*].id", not(hasItem(draftId))));
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
}
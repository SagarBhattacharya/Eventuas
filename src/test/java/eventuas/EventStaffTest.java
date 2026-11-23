package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventStaffTest extends IntegrationTest {

	@Test
	void organizerAssignsAndListsStaffByEmailCaseInsensitively() throws Exception {
		var organizer = TestUser.random("Organizer");
		var staff = TestUser.of("Gate Staff", "gate.staff@eventuas.dev");

		harness.provision(staff);
		String eventId = harness.createDraftEvent(organizer, "Systems Summit");

		harness.postJson("/events/{eventId}/staff", organizer, Map.of("email", "GATE.STAFF@eventuas.dev"), eventId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.eventId").value(eventId))
			.andExpect(jsonPath("$.userId").value(staff.id().toString()));

		mvc.perform(get("/events/{eventId}/staff", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].userId", hasItem(staff.id().toString())))
			.andExpect(jsonPath("$[*].name", hasItem(staff.name())))
			.andExpect(jsonPath("$[*].email", hasItem(staff.email())));
	}

	@Test
	void rejectsAssignmentWhenUserEmailDoesNotExist() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Unknown Staff Conf");

		harness.postJson("/events/{eventId}/staff", organizer, Map.of("email", "nonexistent@eventuas.dev"), eventId)
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsDuplicateStaffAssignmentOnSameEvent() throws Exception {
		var organizer = TestUser.random("Organizer");
		var staff = TestUser.random("DuplicateStaff");

		harness.provision(staff);
		String eventId = harness.createDraftEvent(organizer, "Duplicate Staff Conf");
		harness.assignStaff(organizer, eventId, staff.email());

		harness.postJson("/events/{eventId}/staff", organizer, Map.of("email", staff.email()), eventId)
			.andExpect(status().isConflict());
	}

	@Test
	void organizerRevokesStaffAccess() throws Exception {
		var organizer = TestUser.random("Organizer");
		var staff = TestUser.random("TempStaff");

		harness.provision(staff);
		String eventId = harness.createDraftEvent(organizer, "Staff Rotation Event");
		harness.assignStaff(organizer, eventId, staff.email());

		mvc.perform(delete("/events/{eventId}/staff/{userId}", eventId, staff.id())
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/events/{eventId}/staff", eventId)
				.with(organizer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].userId", not(hasItem(staff.id().toString()))));
	}

	@Test
	void forbidsNonOrganizerFromManagingEventStaff() throws Exception {
		var organizer = TestUser.random("Organizer");
		var intruder = TestUser.random("Intruder");
		var targetStaff = TestUser.random("TargetStaff");

		harness.provision(targetStaff);
		String eventId = harness.createDraftEvent(organizer, "Guarded Event");

		harness.postJson("/events/{eventId}/staff", intruder, Map.of("email", targetStaff.email()), eventId)
			.andExpect(status().isForbidden());

		mvc.perform(get("/events/{eventId}/staff", eventId)
				.with(intruder.jwtPostProcessor()))
			.andExpect(status().isForbidden());

		mvc.perform(delete("/events/{eventId}/staff/{userId}", eventId, targetStaff.id())
				.with(intruder.jwtPostProcessor()))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsMalformedStaffEmail() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createDraftEvent(organizer, "Email Validation Conf");

		harness.postJson("/events/{eventId}/staff", organizer, Map.of("email", "not-an-email"), eventId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void buyerGetsSignedQrTokenAndAssignedStaffChecksTicketIn() throws Exception {
		var organizer = TestUser.random("Organizer");
		var staff = TestUser.of("Gate Staff", "staff@eventuas.dev");
		var buyer = TestUser.random("Buyer");

		harness.provision(staff);
		String eventId = harness.createPublishedEvent(organizer, "Systems Summit");
		harness.assignStaff(organizer, eventId, staff.email());

		String tierId = harness.createTier(organizer, eventId, "General", 1500, 10);
		String ticketId = harness.buyTicket(buyer, tierId);
		String qrToken = harness.fetchQrToken(buyer, ticketId);

		assertThat(qrToken).startsWith(ticketId + ".");

		harness.postJson("/check-ins", staff, Map.of("qrToken", qrToken))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.ticketId").value(ticketId))
			.andExpect(jsonPath("$.checkedInBy").value(staff.id().toString()))
			.andExpect(jsonPath("$.checkedInAt").isNotEmpty());
	}

	@Test
	void organizerCanAlsoCheckInTicketsDirectly() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Founder Meetup");
		String tierId = harness.createTier(organizer, eventId, "Pass", 500, 5);
		String ticketId = harness.buyTicket(buyer, tierId);
		String qrToken = harness.fetchQrToken(buyer, ticketId);

		harness.postJson("/check-ins", organizer, Map.of("qrToken", qrToken))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ticketId").value(ticketId))
			.andExpect(jsonPath("$.checkedInBy").value(organizer.id().toString()));
	}

	@Test
	void forbidsAnotherUserFromFetchingBuyersQrToken() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");
		var intruder = TestUser.random("Intruder");

		String eventId = harness.createPublishedEvent(organizer, "Private QR Conf");
		String tierId = harness.createTier(organizer, eventId, "VIP", 3000, 5);
		String ticketId = harness.buyTicket(buyer, tierId);

		mvc.perform(get("/tickets/{ticketId}/qr", ticketId)
				.with(intruder.jwtPostProcessor()))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsTamperedOrMalformedQrToken() throws Exception {
		var staff = TestUser.random("Staff");
		String tampered = UUID.randomUUID() + ".invalidSignaturePayload";

		harness.postJson("/check-ins", staff, Map.of("qrToken", tampered))
			.andExpect(status().isBadRequest());
	}

	@Test
	void forbidsUnauthorizedScannerFromCheckingInTicket() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");
		var randomUser = TestUser.random("Random");

		String eventId = harness.createPublishedEvent(organizer, "Staff-Only Gate Conf");
		String tierId = harness.createTier(organizer, eventId, "Standard", 1000, 10);
		String ticketId = harness.buyTicket(buyer, tierId);
		String qrToken = harness.fetchQrToken(buyer, ticketId);

		harness.postJson("/check-ins", randomUser, Map.of("qrToken", qrToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsCheckInWhenEventIsClosed() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Closing Event");
		String tierId = harness.createTier(organizer, eventId, "Standard", 1000, 10);
		String ticketId = harness.buyTicket(buyer, tierId);
		String qrToken = harness.fetchQrToken(buyer, ticketId);

		harness.closeEvent(organizer, eventId);

		harness.postJson("/check-ins", organizer, Map.of("qrToken", qrToken))
			.andExpect(status().isConflict());
	}

	@Test
	void allowsOnlySingleCheckInUnderConcurrentScans() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Concurrent Gate Drop");
		String tierId = harness.createTier(organizer, eventId, "VIP", 2500, 5);
		String ticketId = harness.buyTicket(buyer, tierId);
		String qrToken = harness.fetchQrToken(buyer, ticketId);

		int scanners = 10;
		List<TestUser> staffList = new ArrayList<>();
		for (int i = 0; i < scanners; i++) {
			var staff = TestUser.random("Scanner" + i);
			harness.provision(staff);
			harness.assignStaff(organizer, eventId, staff.email());
			staffList.add(staff);
		}

		CountDownLatch ready = new CountDownLatch(scanners);
		CountDownLatch startGate = new CountDownLatch(1);

		List<Callable<Integer>> tasks = new ArrayList<>();
		for (var staff : staffList) {
			tasks.add(() -> {
				ready.countDown();
				startGate.await();
				return harness.postJson("/check-ins", staff, Map.of("qrToken", qrToken))
					.andReturn()
					.getResponse()
					.getStatus();
			});
		}

		List<Integer> statuses = new ArrayList<>();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Integer>> futures = tasks.stream().map(executor::submit).toList();
			ready.await();
			startGate.countDown();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
		}

		assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1L);
		assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(9L);

		Integer rows = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM check_ins WHERE ticket_id = ?::uuid",
			Integer.class,
			ticketId
		);
		assertThat(rows).isEqualTo(1);
	}
}
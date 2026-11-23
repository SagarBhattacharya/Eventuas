package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void rejectsPurchaseWhenEventIsDraftOrClosed() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String draftEventId = harness.createDraftEvent(organizer, "Unpublished Conf");
		String draftTierId = harness.createTier(organizer, draftEventId, "Early", 1000, 5);

		mvc.perform(post("/tiers/{tierId}/buy", draftTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString())
			).andExpect(status().isConflict());

		harness.publishEvent(organizer, draftEventId);
		harness.closeEvent(organizer, draftEventId);

		mvc.perform(post("/tiers/{tierId}/buy", draftTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString())
			).andExpect(status().isConflict());
	}

	@Test
	void rejectsPurchaseWhenTierIsClosedOrSoldOut() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Limited Seat Workshop");
		String singleSeatTierId = harness.createTier(organizer, eventId, "Single Seat", 2000, 1);
		String closedTierId = harness.createTier(organizer, eventId, "Closed Tier", 1000, 10);

		harness.closeTier(organizer, eventId, closedTierId);

		mvc.perform(post("/tiers/{tierId}/buy", closedTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString())
			).andExpect(status().isConflict());

		harness.buyTicket(buyer, singleSeatTierId);

		mvc.perform(post("/tiers/{tierId}/buy", singleSeatTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString())
			).andExpect(status().isConflict());
	}

	@Test
	void preventsOversellingUnderConcurrentPurchases() throws Exception {
		var organizer = TestUser.random("Organizer");
		String eventId = harness.createPublishedEvent(organizer, "High Demand Drop");
		String tierId = harness.createTier(organizer, eventId, "VIP Drop", 5000, 5);

		int contenders = 20;
		CountDownLatch ready = new CountDownLatch(contenders);
		CountDownLatch startGate = new CountDownLatch(1);

		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < contenders; i++) {
			var buyer = TestUser.random("Buyer" + i);
			tasks.add(() -> {
				ready.countDown();
				startGate.await();
				return mvc.perform(post("/tiers/{tierId}/buy", tierId)
						.with(buyer.jwtPostProcessor())
						.header("Idempotency-Key", UUID.randomUUID().toString()))
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

		assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(5L);
		assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(15L);

		Integer persistedTickets = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM tickets WHERE tier_id = ?::uuid",
			Integer.class,
			tierId
		);
		Long remainingQty = jdbcTemplate.queryForObject(
			"SELECT available_qty FROM ticket_tiers WHERE id = ?::uuid",
			Long.class,
			tierId
		);

		assertThat(persistedTickets).isEqualTo(5);
		assertThat(remainingQty).isEqualTo(0L);
	}

	@Test
	void rejectsPurchaseOutsideEventSalesWindow() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");
		Instant eventStart = Instant.now().plus(10, ChronoUnit.DAYS);

		// 1. Sales have not started yet
		String upcomingResp = harness.postJson("/events", organizer, Map.of(
				"name", "Future Sales Conf",
				"description", "Sales open tomorrow",
				"venue", "Main Hall",
				"startDate", eventStart.toString(),
				"endDate", eventStart.plus(3, ChronoUnit.HOURS).toString(),
				"salesStartDate", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
				"salesEndDate", Instant.now().plus(5, ChronoUnit.DAYS).toString()
			))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String upcomingEventId = new tools.jackson.databind.ObjectMapper()
			.readTree(upcomingResp).get("id").asString();
		String upcomingTierId = harness.createTier(organizer, upcomingEventId, "Early", 1000, 10);
		harness.publishEvent(organizer, upcomingEventId);

		mvc.perform(post("/tiers/{tierId}/buy", upcomingTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isConflict());

		// 2. Sales window has already ended
		String expiredResp = harness.postJson("/events", organizer, Map.of(
				"name", "Ended Sales Conf",
				"description", "Sales closed yesterday",
				"venue", "Main Hall",
				"startDate", eventStart.toString(),
				"endDate", eventStart.plus(3, ChronoUnit.HOURS).toString(),
				"salesStartDate", Instant.now().minus(5, ChronoUnit.DAYS).toString(),
				"salesEndDate", Instant.now().minus(1, ChronoUnit.HOURS).toString()
			))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String expiredEventId = new tools.jackson.databind.ObjectMapper()
			.readTree(expiredResp).get("id").asString();
		String expiredTierId = harness.createTier(organizer, expiredEventId, "Standard", 1500, 10);
		harness.publishEvent(organizer, expiredEventId);

		mvc.perform(post("/tiers/{tierId}/buy", expiredTierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isConflict());
	}

	@Test
	void buyerPurchasesTicketAndDecrementsStock() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Systems Conf");
		String tierId = harness.createTier(organizer, eventId, "General", 1500, 10);

		String ticketId = harness.buyTicket(buyer, tierId);

		Long remaining = jdbcTemplate.queryForObject(
			"SELECT available_qty FROM ticket_tiers WHERE id = ?::uuid",
			Long.class,
			tierId
		);
		assertThat(remaining).isEqualTo(9L);

		mvc.perform(get("/tickets/me")
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].ticketId", hasItem(ticketId)));
	}

	@Test
	void searchesFiltersAndPaginatesBuyerTickets() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String rustEventId = harness.createPublishedEvent(organizer, "Rust Kolkata Meetup");
		String rustTierId = harness.createTier(organizer, rustEventId, "Pass", 500, 10);

		String goEventId = harness.createPublishedEvent(organizer, "Go Systems Conf");
		String goTierId = harness.createTier(organizer, goEventId, "Pass", 800, 10);

		String checkedInTicket = harness.buyTicket(buyer, rustTierId);
		String unusedTicket = harness.buyTicket(buyer, goTierId);

		String qrToken = harness.fetchQrToken(buyer, checkedInTicket);
		harness.checkIn(organizer, qrToken);

		// 1. Paginate across all buyer tickets (2 total, size=1)
		mvc.perform(get("/tickets/me")
				.with(buyer.jwtPostProcessor())
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));

		// 2. Filter by checkedIn=true and event keyword
		mvc.perform(get("/tickets/me")
				.with(buyer.jwtPostProcessor())
				.param("q", "Rust")
				.param("checkedIn", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].ticketId").value(checkedInTicket))
			.andExpect(jsonPath("$.items[0].checkedIn").value(true));

		// 3. Filter by checkedIn=false
		mvc.perform(get("/tickets/me")
				.with(buyer.jwtPostProcessor())
				.param("checkedIn", "false"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].ticketId").value(unusedTicket))
			.andExpect(jsonPath("$.items[0].checkedIn").value(false));
	}

	@Test
	void rejectsPurchaseWithoutIdempotencyKey() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String eventId = harness.createPublishedEvent(organizer, "Header Required Conf");
		String tierId = harness.createTier(organizer, eventId, "General", 1500, 10);

		mvc.perform(post("/tiers/{tierId}/buy", tierId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isBadRequest());

		mvc.perform(post("/tiers/{tierId}/buy", tierId)
				.with(buyer.jwtPostProcessor())
				.header("Idempotency-Key", "   "))
			.andExpect(status().isBadRequest());
	}

	@Test
	void idempotencyKeyDeduplicatesRepeatPurchasesPerUser() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyerA = TestUser.random("BuyerA");
		var buyerB = TestUser.random("BuyerB");

		String eventId = harness.createPublishedEvent(organizer, "Idempotent Conf");
		String tierId = harness.createTier(organizer, eventId, "General", 1500, 10);
		String sharedKey = "idem-" + UUID.randomUUID();

		String firstTicketId = harness.buyTicket(buyerA, tierId, sharedKey);
		String retryTicketId = harness.buyTicket(buyerA, tierId, sharedKey);

		assertThat(retryTicketId).isEqualTo(firstTicketId);

		Long remainingAfterRetry = jdbcTemplate.queryForObject(
			"SELECT available_qty FROM ticket_tiers WHERE id = ?::uuid",
			Long.class,
			tierId
		);
		assertThat(remainingAfterRetry).isEqualTo(9L);

		// Same key string for a different buyer is isolated per user
		String buyerBTicketId = harness.buyTicket(buyerB, tierId, sharedKey);
		assertThat(buyerBTicketId).isNotEqualTo(firstTicketId);
	}
}
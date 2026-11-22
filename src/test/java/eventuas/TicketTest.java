package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

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
			.andExpect(jsonPath("$[*].id", hasItem(ticketId)));
	}

	@Test
	void rejectsPurchaseWhenEventIsDraftOrClosed() throws Exception {
		var organizer = TestUser.random("Organizer");
		var buyer = TestUser.random("Buyer");

		String draftEventId = harness.createDraftEvent(organizer, "Unpublished Conf");
		String draftTierId = harness.createTier(organizer, draftEventId, "Early", 1000, 5);

		mvc.perform(post("/tiers/{tierId}/buy", draftTierId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isConflict());

		harness.publishEvent(organizer, draftEventId);
		harness.closeEvent(organizer, draftEventId);

		mvc.perform(post("/tiers/{tierId}/buy", draftTierId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isConflict());
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
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isConflict());

		harness.buyTicket(buyer, singleSeatTierId);

		mvc.perform(post("/tiers/{tierId}/buy", singleSeatTierId)
				.with(buyer.jwtPostProcessor()))
			.andExpect(status().isConflict());
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
						.with(buyer.jwtPostProcessor()))
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
}
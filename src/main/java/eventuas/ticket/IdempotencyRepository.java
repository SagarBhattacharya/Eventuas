package eventuas.ticket;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotencyRepository {

	private final JdbcClient jdbcClient;

	IdempotencyRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	boolean tryClaim(UUID userId, String key, Instant createdAt) {
		int inserted = jdbcClient.sql("""
			INSERT INTO idempotency_keys (user_id, idempotency_key, ticket_id, created_at)
			VALUES (:userId, :key, NULL, :createdAt)
			ON CONFLICT (user_id, idempotency_key) DO NOTHING
		""")
			.param("userId", userId)
			.param("key", key)
			.param("createdAt", Timestamp.from(createdAt))
			.update();
		return inserted == 1;
	}

	Optional<UUID> findTicketId(UUID userId, String key) {
		return jdbcClient.sql("""
			SELECT ticket_id
			FROM idempotency_keys
			WHERE user_id = :userId
			  AND idempotency_key = :key
			  AND ticket_id IS NOT NULL
		""")
			.param("userId", userId)
			.param("key", key)
			.query(UUID.class)
			.optional();
	}

	void bindTicket(UUID userId, String key, UUID ticketId) {
		jdbcClient.sql("""
			UPDATE idempotency_keys
				SET ticket_id = :ticketId
			WHERE user_id = :userId
			  AND idempotency_key = :key
		""")
			.param("ticketId", ticketId)
			.param("userId", userId)
			.param("key", key)
			.update();
	}
}
package eventuas.ticket;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends CrudRepository<Ticket, UUID> {
	@Query("""
		SELECT
			t.id AS ticket_id,
			e.id AS event_id,
			e.name AS event_name,
			e.venue AS venue,
			e.start_date AS start_date,
			e.end_date AS end_date,
			tt.tier AS tier_name,
			tt.price AS price,
			tt.currency AS currency,
			(ci.id IS NOT NULL) AS checked_in,
			ci.checked_in_at AS checked_in_at,
			t.created_at AS purchased_at
		FROM tickets t
		JOIN ticket_tiers tt ON tt.id = t.tier_id
		JOIN events e ON e.id = tt.event_id
		LEFT JOIN check_ins ci ON ci.ticket_id = t.id
		WHERE t.purchased_by = :buyerId
		ORDER BY t.created_at DESC
	""")
	List<TicketDto.Summary> findSummariesByBuyer(UUID buyerId);
}
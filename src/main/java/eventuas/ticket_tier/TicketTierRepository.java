package eventuas.ticket_tier;

import eventuas.event.EventDto;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTierRepository extends CrudRepository<TicketTier, UUID> {

	List<TicketTier> findByEventIdOrderByCreatedAtAsc(UUID eventId);

	@Query("""
		SELECT * FROM ticket_tiers
		WHERE event_id = :eventId
			AND status = 'ACTIVE'
		ORDER BY price
	""")
	List<TicketTier> findActiveByEventId(UUID eventId);

	Optional<TicketTier> findByIdAndEventId(UUID id, UUID eventId);

	@Modifying
	@Query("""
		UPDATE ticket_tiers SET
			available_qty = available_qty - 1,
			updated_at = now()
		WHERE id = :tierId
			AND status = 'ACTIVE'
			AND available_qty > 0
		""")
	int decrementStock(UUID tierId);

	@Modifying
	@Query("""
		UPDATE ticket_tiers SET
			status = 'CLOSED',
			updated_at = now()
		WHERE event_id = :eventId
			AND status = 'ACTIVE'
		""")
	int closeAllActiveByEventId(UUID eventId);

	@Query("""
	SELECT
		tt.id AS tier_id,
		tt.tier AS tier_name,
		tt.status AS status,
		tt.price AS price,
		tt.currency AS currency,
		COUNT(t.id) AS sold_count,
		tt.available_qty AS remaining_qty,
		COUNT(ci.id) AS checked_in_count,
		(COUNT(t.id) * tt.price) AS revenue
	FROM ticket_tiers tt
	LEFT JOIN tickets t ON t.tier_id = tt.id
	LEFT JOIN check_ins ci ON ci.ticket_id = t.id
	WHERE tt.event_id = :eventId
	GROUP BY tt.id, tt.tier, tt.status, tt.price, tt.currency, tt.available_qty, tt.created_at
	ORDER BY tt.created_at
	""")
	List<EventDto.TierStats> findStatsByEventId(UUID eventId);
}
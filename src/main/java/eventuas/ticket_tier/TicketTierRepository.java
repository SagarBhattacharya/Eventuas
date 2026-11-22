package eventuas.ticket_tier;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTierRepository extends CrudRepository<TicketTier, UUID> {

	List<TicketTier> findByEventIdOrderByCreatedAtAsc(UUID eventId);

	@Query("SELECT * FROM ticket_tiers WHERE event_id = :eventId AND status = 'ACTIVE' ORDER BY price")
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
}
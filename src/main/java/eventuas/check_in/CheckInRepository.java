package eventuas.check_in;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends CrudRepository<CheckIn, UUID> {

	@Query("""
		INSERT INTO check_ins (ticket_id, checked_in_by, checked_in_at)
		VALUES (:ticketId, :checkedInBy, :checkedInAt)
		ON CONFLICT (ticket_id) DO NOTHING
		RETURNING *
		""")
	Optional<CheckIn> tryCheckIn(UUID ticketId, UUID checkedInBy, Instant checkedInAt);
}
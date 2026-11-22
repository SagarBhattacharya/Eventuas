package eventuas.event;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends CrudRepository<Event, UUID> {

	List<Event> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

	@Query("SELECT * FROM events WHERE status <> 'DRAFT' ORDER BY start_date")
	List<Event> findAllPublished();
}
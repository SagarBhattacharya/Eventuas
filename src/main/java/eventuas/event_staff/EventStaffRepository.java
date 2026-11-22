package eventuas.event_staff;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface EventStaffRepository extends CrudRepository<EventStaff, UUID> {

	List<EventStaff> findByEventIdOrderByCreatedAtAsc(UUID eventId);

	boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

	@Modifying
	@Query("DELETE FROM event_staffs WHERE event_id = :eventId AND user_id = :userId")
	int deleteByEventIdAndUserId(UUID eventId, UUID userId);
}

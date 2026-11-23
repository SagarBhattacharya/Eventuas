package eventuas.event_staff;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface EventStaffRepository extends CrudRepository<EventStaff, UUID> {
	@Query("""
		SELECT
			es.id AS id,
			es.event_id AS event_id,
			es.user_id AS user_id,
			u.name AS name,
			u.email AS email,
			es.created_at AS assigned_at
		FROM event_staffs es
		JOIN users u ON u.id = es.user_id
		WHERE es.event_id = :eventId
		ORDER BY es.created_at
		""")
	List<EventStaffDto.Member> findMembersByEventId(UUID eventId);

	boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

	@Modifying
	@Query("DELETE FROM event_staffs WHERE event_id = :eventId AND user_id = :userId")
	int deleteByEventIdAndUserId(UUID eventId, UUID userId);
}
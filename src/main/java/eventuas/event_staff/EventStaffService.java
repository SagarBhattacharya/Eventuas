package eventuas.event_staff;

import eventuas.errors.AppException;
import eventuas.event.EventAccess;
import eventuas.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class EventStaffService {

	private final EventAccess eventAccess;
	private final UserRepository userRepository;
	private final EventStaffRepository eventStaffRepository;
	private final Clock clock;

	public EventStaffService(
		EventAccess eventAccess,
		UserRepository userRepository,
		EventStaffRepository eventStaffRepository,
		Clock clock
	) {
		this.eventAccess = eventAccess;
		this.userRepository = userRepository;
		this.eventStaffRepository = eventStaffRepository;
		this.clock = clock;
	}

	@Transactional
	public EventStaff assignStaff(UUID organizerId, UUID eventId, String email) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "assign staff to");

		var staffUser = userRepository.findByEmailIgnoreCase(email.trim())
			.orElseThrow(AppException.UserNotFound::new);

		try {
			return eventStaffRepository.save(
				EventStaff.assign(eventId, staffUser.id(), clock.instant())
			);
		} catch (DataIntegrityViolationException ex) {
			throw new AppException.StaffAlreadyAssigned();
		}
	}

	public List<EventStaff> listStaff(UUID organizerId, UUID eventId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "list staff for");
		return eventStaffRepository.findByEventIdOrderByCreatedAtAsc(eventId);
	}

	@Transactional
	public void revokeStaff(UUID organizerId, UUID eventId, UUID staffUserId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "revoke staff from");
		eventStaffRepository.deleteByEventIdAndUserId(eventId, staffUserId);
	}
}
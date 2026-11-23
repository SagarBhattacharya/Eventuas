package eventuas.event_staff;

import eventuas.errors.AppException;
import eventuas.event.EventAccess;
import eventuas.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class EventStaffService {

	private static final Logger log = LoggerFactory.getLogger(EventStaffService.class);
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
		var event = eventAccess.requireOwned(organizerId, eventId, "assign staff to");

		var staffUser = userRepository.findByEmailIgnoreCase(email.trim())
			.orElseThrow(AppException.UserNotFound::new);

		try {
			var saved = eventStaffRepository.save(
				EventStaff.assign(eventId, staffUser.id(), clock.instant())
			);

			log.atInfo()
				.setMessage("event.staff.assigned")
				.addKeyValue("eventId", event.id())
				.addKeyValue("eventStaffId", staffUser.id())
				.addKeyValue("organizerId", organizerId)
				.log();

			return saved;
		} catch (DataIntegrityViolationException ex) {
			throw new AppException.StaffAlreadyAssigned();
		}
	}

	public List<EventStaffDto.Member> listStaff(UUID organizerId, UUID eventId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "list staff for");
		return eventStaffRepository.findMembersByEventId(eventId);
	}

	@Transactional
	public void revokeStaff(UUID organizerId, UUID eventId, UUID staffUserId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "revoke staff from");
		var _ = eventStaffRepository.deleteByEventIdAndUserId(eventId, staffUserId);

		log.atInfo()
			.setMessage("event.staff.revoked")
			.addKeyValue("eventId", eventId)
			.addKeyValue("eventStaffId", staffUserId)
			.addKeyValue("organizerId", organizerId)
			.log();
	}
}
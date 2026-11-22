package eventuas.event;

import eventuas.errors.AppException;
import eventuas.event_staff.EventStaffRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventAccess {

	private final EventRepository eventRepository;
	private final EventStaffRepository eventStaffRepository;

	public EventAccess(
		EventRepository eventRepository,
		EventStaffRepository eventStaffRepository
	) {
		this.eventRepository = eventRepository;
		this.eventStaffRepository = eventStaffRepository;
	}

	public Event requireOwned(UUID organizerId, UUID eventId, String action) {
		var event = eventRepository.findById(eventId)
			.orElseThrow(AppException.EventNotFound::new);
		event.ensureOwnedBy(organizerId, action);
		return event;
	}

	public Event requireStaffOrOwner(UUID userId, UUID eventId, String action) {
		var event = eventRepository.findById(eventId)
			.orElseThrow(AppException.EventNotFound::new);

		boolean isOwner = userId.equals(event.organizerId());
		boolean isStaff = !isOwner &&
			eventStaffRepository.existsByEventIdAndUserId(eventId, userId);

		if (!isOwner && !isStaff) {
			throw new AppException.EventNotAuthorized(action);
		}
		return event;
	}
}
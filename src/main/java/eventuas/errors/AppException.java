package eventuas.errors;

public sealed abstract class AppException extends RuntimeException {
	protected AppException(String message) {
		super(message);
	}

	public static final class UserNotFound extends AppException {
		public UserNotFound() {
			super("User not found.");
		}
	}

	public static final class EventNotFound extends AppException {
		public EventNotFound() {
			super("Event not found.");
		}
	}

	public static final class EventNotAuthorized extends AppException {
		public EventNotAuthorized(String action) {
			super("You are not authorized to %s this event.".formatted(action));
		}
	}

	public static final class EventNotPublishable extends AppException {
		public EventNotPublishable(String reason) {
			super("Event is already %s: Cannot publish.".formatted(reason));
		}
	}

	public static final class EventAlreadyClosed extends AppException {
		public EventAlreadyClosed() {
			super("Event is already closed.");
		}
	}

	public static final class TierAlreadyClosed extends AppException {
		public TierAlreadyClosed() {
			super("Ticket Tier is already closed.");
		}
	}

	public static final class TierNotFound extends AppException {
		public TierNotFound() {
			super("Tier not found.");
		}
	}

	public static final class TicketNotFound extends AppException {
		public TicketNotFound() {
			super("Ticket not found.");
		}
	}

	public static final class TierSoldOut extends AppException {
		public TierSoldOut() {
			super("Tickets for this tier are sold out.");
		}
	}

	public static final class EventNotActive extends AppException {
		public EventNotActive() {
			super("Event is not active for ticket purchases.");
		}
	}

	public static final class StaffAlreadyAssigned extends AppException {
		public StaffAlreadyAssigned() {
			super("User is already assigned as staff for this event.");
		}
	}

	public static final class InvalidQrToken extends AppException {
		public InvalidQrToken() {
			super("Invalid or malformed QR token.");
		}
	}

	public static final class TicketNotOwned extends AppException {
		public TicketNotOwned() {
			super("You do not own this ticket.");
		}
	}

	public static final class TicketAlreadyCheckedIn extends AppException {
		public TicketAlreadyCheckedIn() {
			super("Ticket has already been checked in.");
		}
	}

	public static final class SalesNotOpen extends AppException {
		public SalesNotOpen() {
			super("Ticket sales are not currently open for this event.");
		}
	}
}
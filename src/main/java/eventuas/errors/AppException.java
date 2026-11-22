package eventuas.errors;

public sealed abstract class AppException extends RuntimeException {
	private final ErrorCode code;

	protected AppException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}

	public static final class UserNotFound extends AppException {
		public UserNotFound() {
			super(ErrorCode.USER_NOT_FOUND, "User not found.");
		}
	}

	public static final class EventNotFound extends AppException {
		public EventNotFound() {
			super(ErrorCode.EVENT_NOT_FOUND, "Event not found.");
		}
	}

	public static final class EventNotAuthorized extends AppException {
		public EventNotAuthorized(String action) {
			super(
				ErrorCode.EVENT_NOT_AUTHORIZED,
				"You are not authorized to %s this event.".formatted(action)
			);
		}
	}

	public static final class EventNotPublishable extends AppException {
		public EventNotPublishable(String reason) {
			super(
				ErrorCode.EVENT_NOT_PUBLISHABLE,
				"Event is already %s: Cannot publish.".formatted(reason)
			);
		}
	}

	public static final class EventAlreadyClosed extends AppException {
		public EventAlreadyClosed() {
			super(ErrorCode.EVENT_ALREADY_CLOSED, "Event is already closed.");
		}
	}

	public static final class TierAlreadyClosed extends AppException {
		public TierAlreadyClosed() {
			super(ErrorCode.TIER_ALREADY_CLOSED, "Ticket Tier is already closed.");
		}
	}

	public static final class TierNotFound extends AppException {
		public TierNotFound() {
			super(ErrorCode.TIER_NOT_FOUND, "Tier not found.");
		}
	}

	public static final class TicketNotFound extends AppException {
		public TicketNotFound() {
			super(ErrorCode.TICKET_NOT_FOUND, "Ticket not found.");
		}
	}

	public static final class TierSoldOut extends AppException {
		public TierSoldOut() {
			super(ErrorCode.TIER_SOLD_OUT, "Tickets for this tier are sold out.");
		}
	}

	public static final class EventNotActive extends AppException {
		public EventNotActive() {
			super(
				ErrorCode.EVENT_NOT_ACTIVE,
				"Event is not active for ticket purchases."
			);
		}
	}

	public static final class StaffAlreadyAssigned extends AppException {
		public StaffAlreadyAssigned() {
			super(
				ErrorCode.STAFF_ALREADY_ASSIGNED,
				"User is already assigned as staff for this event."
			);
		}
	}

	public static final class InvalidQrToken extends AppException {
		public InvalidQrToken() {
			super(ErrorCode.INVALID_QR_TOKEN, "Invalid or malformed QR token.");
		}
	}

	public static final class TicketNotOwned extends AppException {
		public TicketNotOwned() {
			super(ErrorCode.TICKET_NOT_OWNED, "You do not own this ticket.");
		}
	}

	public static final class TicketAlreadyCheckedIn extends AppException {
		public TicketAlreadyCheckedIn() {
			super(
				ErrorCode.TICKET_ALREADY_CHECKED_IN,
				"Ticket has already been checked in."
			);
		}
	}

	public static final class SalesNotOpen extends AppException {
		public SalesNotOpen() {
			super(
				ErrorCode.SALES_NOT_OPEN,
				"Ticket sales are not currently open for this event."
			);
		}
	}

	public static final class InvalidEventWindow extends AppException {
		public InvalidEventWindow() {
			super(
				ErrorCode.VALIDATION_ERROR,
				"Event end date must be after start date."
			);
		}
	}

	public static final class InvalidSalesWindow extends AppException {
		public InvalidSalesWindow() {
			super(
				ErrorCode.VALIDATION_ERROR,
				"Sales end date must be after sales start date."
			);
		}
	}
}
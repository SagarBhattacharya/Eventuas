package eventuas.errors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AppException.class)
	public ProblemDetail handleAppException(AppException ex) {
		HttpStatus status = switch (ex) {
			case AppException.EventNotFound _,
					 AppException.TierNotFound _,
					 AppException.TicketNotFound _,
					 AppException.UserNotFound _ -> HttpStatus.NOT_FOUND;
			case AppException.EventNotAuthorized _,
					 AppException.TicketNotOwned _ -> HttpStatus.FORBIDDEN;
			case AppException.EventNotPublishable _,
					 AppException.EventAlreadyClosed _,
					 AppException.TierAlreadyClosed _,
					 AppException.EventNotActive _,
					 AppException.TierSoldOut _,
					 AppException.StaffAlreadyAssigned _,
					 AppException.TicketAlreadyCheckedIn _,
					 AppException.SalesNotOpen _ -> HttpStatus.CONFLICT;
			case AppException.InvalidQrToken _ -> HttpStatus.BAD_REQUEST;
		};
		return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		Throwable cause = ex.getMostSpecificCause();
		if (cause instanceof java.sql.SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
			return ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT,
				"An active tier with this name already exists for this event."
			);
		}
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid data constraint.");
	}
}
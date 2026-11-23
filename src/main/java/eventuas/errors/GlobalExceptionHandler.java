package eventuas.errors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.stream.Collectors;

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
					 AppException.CheckInWindowClosed _,
					 AppException.EventHasNoActiveTiers _,
					 AppException.TicketAlreadyCheckedIn _,
					 AppException.SalesNotOpen _ -> HttpStatus.CONFLICT;
			case AppException.InvalidQrToken _,
					 AppException.InvalidSalesWindow _,
					 AppException.InvalidEventWindow _ -> HttpStatus.BAD_REQUEST;
		};
		return problem(status, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
			.map(err -> err.getField() + ": " + err.getDefaultMessage())
			.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		if (ex.getMostSpecificCause() instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
			return problem(
				HttpStatus.CONFLICT,
				ErrorCode.DUPLICATE_TIER_NAME,
				"An active tier with this name already exists for this event."
			);
		}
		return problem(HttpStatus.BAD_REQUEST, ErrorCode.DATA_INTEGRITY_VIOLATION, "Invalid data constraint.");
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code);
		return pd;
	}
}
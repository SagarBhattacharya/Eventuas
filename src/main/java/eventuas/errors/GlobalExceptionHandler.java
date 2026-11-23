package eventuas.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
					 AppException.DuplicateTierName _,
					 AppException.SalesNotOpen _ -> HttpStatus.CONFLICT;
			case AppException.InvalidQrToken _,
					 AppException.InvalidSalesWindow _,
					 AppException.InvalidIdempotencyKey _,
					 AppException.InvalidEventWindow _ -> HttpStatus.BAD_REQUEST;
			case AppException.RateLimitExceeded _ -> HttpStatus.TOO_MANY_REQUESTS;
		};
		log.error("App error [{}]: {}", ex.code(), ex.getMessage());
		return problem(status, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		log.error("Validation error: {}", ex.getMessage());
		String detail = ex.getBindingResult().getFieldErrors().stream()
			.map(err -> err.getField() + ": " + err.getDefaultMessage())
			.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(
		DataIntegrityViolationException ex
	) {
		log.error("Data integrity violation: {}", ex.getMessage());
		return problem(
			HttpStatus.CONFLICT,
			ErrorCode.DATA_INTEGRITY_VIOLATION,
			"Request conflicts with a database integrity constraint."
		);
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code);
		return pd;
	}
}
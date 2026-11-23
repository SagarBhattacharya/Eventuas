package eventuas.errors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

		log.atWarn()
			.setMessage("request.rejected")
			.addKeyValue("code", ex.code())
			.addKeyValue("status", status.value())
			.addKeyValue("detail", ex.getMessage())
			.log();

		return problem(status, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
			.map(err -> err.getField() + ": " + err.getDefaultMessage())
			.collect(Collectors.joining("; "));

		log.atWarn()
			.setMessage("request.rejected.validation")
			.addKeyValue("code", ErrorCode.VALIDATION_ERROR)
			.addKeyValue("status", HttpStatus.BAD_REQUEST.value())
			.addKeyValue("detail", detail)
			.log();

		return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.atWarn()
			.setMessage("request.conflict.integrity")
			.addKeyValue("code", ErrorCode.DATA_INTEGRITY_VIOLATION)
			.addKeyValue("status", HttpStatus.CONFLICT.value())
			.setCause(ex)
			.log();

		return problem(
			HttpStatus.CONFLICT,
			ErrorCode.DATA_INTEGRITY_VIOLATION,
			"Request conflicts with a database integrity constraint."
		);
	}

	@ExceptionHandler({
		MissingRequestHeaderException.class,
		MissingServletRequestParameterException.class,
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class
	})
	public ProblemDetail handleBadRequest(Exception ex) {
		String detail = switch (ex) {
			case MissingRequestHeaderException e -> "Missing required header: " + e.getHeaderName();
			case MissingServletRequestParameterException e -> "Missing required parameter: " + e.getParameterName();
			case MethodArgumentTypeMismatchException e -> "Invalid value for parameter: " + e.getName();
			case HttpMessageNotReadableException _ -> "Malformed JSON request body.";
			default -> "Invalid request.";
		};

		log.atWarn()
			.setMessage("request.rejected.bad_request")
			.addKeyValue("code", ErrorCode.VALIDATION_ERROR)
			.addKeyValue("status", HttpStatus.BAD_REQUEST.value())
			.addKeyValue("detail", detail)
			.log();

		return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnhandled(Exception ex) {
		log.atError()
			.setMessage("request.failed")
			.setCause(ex)
			.log();

		return problem(
			HttpStatus.INTERNAL_SERVER_ERROR,
			ErrorCode.INTERNAL_ERROR,
			"An unexpected internal error occurred."
		);
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code);
		return pd;
	}
}
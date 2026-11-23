package eventuas.check_in;

import eventuas.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/check-ins")
public class CheckInController {

	private final CheckInService checkInService;
	private final CheckInRateLimiter rateLimiter;

	public CheckInController(
		CheckInService checkInService,
		CheckInRateLimiter rateLimiter
	) {
		this.checkInService = checkInService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CheckInDto.Response checkIn(
		@CurrentUser UUID scannerId,
		@Valid @RequestBody CheckInDto.Request request
	) {
		rateLimiter.acquire(scannerId);
		return CheckInDto.Response.from(checkInService.checkIn(scannerId, request.qrToken()));
	}
}
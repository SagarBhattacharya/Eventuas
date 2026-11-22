package eventuas.check_in;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/check-ins")
public class CheckInController {

	private final CheckInService checkInService;

	public CheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CheckIn checkIn(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody CheckInRequest request
	) {
		var scannerId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return checkInService.checkIn(scannerId, request.qrToken());
	}
}
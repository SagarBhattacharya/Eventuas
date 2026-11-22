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

	public CheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CheckInDto.Response checkIn(
		@CurrentUser UUID scannerId,
		@Valid @RequestBody CheckInDto.Request request
	) {
		return CheckInDto.Response.from(
			checkInService.checkIn(scannerId, request.qrToken())
		);
	}
}
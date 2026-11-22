package eventuas.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;

@Schema(
	name = "ApiError",
	description = "RFC 9457 Problem Details with machine-readable error code"
)
public record ApiErrorResponse(
	@Schema(example = "about:blank") URI type,
	@Schema(example = "Conflict") String title,
	@Schema(example = "409") int status,
	@Schema(example = "Tickets for this tier are sold out.") String detail,
	@Schema(example = "/tiers/123/buy") URI instance,
	ErrorCode code
) {}
package eventuas.security;

import eventuas.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Component
public class UserProvisionFilter extends OncePerRequestFilter {

	private final UserRepository userRepository;

	public UserProvisionFilter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			Jwt jwt = jwtAuth.getToken();
			UUID userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
			String name = firstNonBlank(
				jwt.getClaimAsString("name"),
				jwt.getClaimAsString("preferred_username"),
				"User"
			);
			String email = firstNonBlank(
				jwt.getClaimAsString("email"),
				userId + "@local.invalid"
			);
			var _ = userRepository.upsert(userId, name, email);
		}

		filterChain.doFilter(request, response);
	}

	private static String firstNonBlank(String... candidates) {
		for (String c : candidates) {
			if (c != null && !c.isBlank()) {
				return c;
			}
		}
		return "";
	}
}
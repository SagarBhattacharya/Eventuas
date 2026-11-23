package eventuas.config;

import eventuas.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserProvisionFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(UserProvisionFilter.class);
	private static final Duration CACHE_TTL = Duration.ofMinutes(5);
	private static final int MAX_CACHE_ENTRIES = 10_000;
	private static final String DEFAULT_NAME_SENTINEL = "Unknown User";

	private record CachedClaims(String name, String email, Instant expiresAt) {
		boolean isValid(String currentName, String currentEmail, Instant now) {
			return now.isBefore(expiresAt)
				&& name.equals(currentName)
				&& email.equals(currentEmail);
		}
	}

	private final UserRepository userRepository;
	private final Clock clock;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<UUID, CachedClaims> cache = new ConcurrentHashMap<>();

	public UserProvisionFilter(
		UserRepository userRepository,
		Clock clock,
		ObjectMapper objectMapper
	) {
		this.userRepository = userRepository;
		this.clock = clock;
		this.objectMapper = objectMapper;
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

			UUID userId;
			try {
				String sub = jwt.getSubject();
				if (sub == null || sub.isBlank()) {
					writeUnauthorized(request, response, "JWT subject claim is missing");
					return;
				}
				userId = UUID.fromString(sub);
			} catch (IllegalArgumentException ex) {
				log.warn("Rejected JWT with non-UUID subject claim: {}", jwt.getSubject());
				writeUnauthorized(request, response, "JWT subject must be a valid UUID");
				return;
			}

			String name = resolveName(jwt);
			String email = resolveEmail(jwt, userId);
			Instant now = clock.instant();

			CachedClaims cached = cache.get(userId);
			if (cached == null || !cached.isValid(name, email, now)) {
				userRepository.upsert(userId, name, email);
				if (cache.size() >= MAX_CACHE_ENTRIES) {
					cache.clear();
				}
				cache.put(userId, new CachedClaims(name, email, now.plus(CACHE_TTL)));
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveName(Jwt jwt) {
		String candidate = firstNonBlank(
			jwt.getClaimAsString("name"),
			jwt.getClaimAsString("preferred_username")
		);
		return candidate != null ? candidate : DEFAULT_NAME_SENTINEL;
	}

	private String resolveEmail(Jwt jwt, UUID userId) {
		String email = firstNonBlank(jwt.getClaimAsString("email"));
		if (email != null) {
			return email;
		}
		log.warn("JWT for user {} is missing an email claim; using fallback sentinel", userId);
		return "missing-" + userId + "@unverified.local";
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private void writeUnauthorized(
		HttpServletRequest request,
		HttpServletResponse response,
		String detail
	) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
		problem.setTitle("Unauthorized");
		problem.setType(URI.create("urn:eventuas:error:INVALID_TOKEN"));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", "INVALID_TOKEN");

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), problem);
	}
}
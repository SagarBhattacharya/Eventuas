package eventuas.user;

import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public final class CurrentUserArgumentResolver
	implements HandlerMethodArgumentResolver
{

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class)
			&& UUID.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(
		@NonNull MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		@NonNull NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
			throw new AuthenticationCredentialsNotFoundException("Missing authenticated JWT principal");
		}

		String subject = jwt.getSubject();
		if (subject == null || subject.isBlank()) {
			throw new BadCredentialsException("JWT subject claim is missing");
		}

		try {
			return UUID.fromString(subject);
		} catch (IllegalArgumentException ex) {
			throw new BadCredentialsException("JWT subject claim is not a valid UUID", ex);
		}
	}
}
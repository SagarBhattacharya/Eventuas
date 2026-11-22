package eventuas.integration;

import org.springframework.security.test.web.servlet
	.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet
	.request.SecurityMockMvcRequestPostProcessors.jwt;

public record TestUser(UUID id, String name, String email) {

	public static TestUser of(String name, String email) {
		return new TestUser(UUID.randomUUID(), name, email);
	}

	public static TestUser random(String prefix) {
		UUID id = UUID.randomUUID();
		String slug = prefix.toLowerCase().replaceAll("[^a-z0-9]+", ".");
		return new TestUser(id, prefix, slug + "." + id.toString().substring(0, 8) + "@eventuas.dev");
	}

	public JwtRequestPostProcessor jwtPostProcessor() {
		return jwt().jwt(j -> j
			.subject(id.toString())
			.claim("name", name)
			.claim("email", email)
		);
	}
}
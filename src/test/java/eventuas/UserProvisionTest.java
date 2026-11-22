package eventuas;

import eventuas.integration.IntegrationTest;
import eventuas.integration.TestUser;
import eventuas.user.User;
import eventuas.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProvisionTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Test
	void authenticatesAndAutoProvisionsUser() throws Exception {
		var user = TestUser.of("Sagar", "sagar@eventuas.dev");

		mvc.perform(get("/users/me")
				.with(user.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.id().toString()))
			.andExpect(jsonPath("$.name").value("Sagar"))
			.andExpect(jsonPath("$.email").value("sagar@eventuas.dev"));

		assertThat(userRepository.findById(user.id()))
			.isPresent()
			.get()
			.extracting(User::name, User::email)
			.containsExactly("Sagar", "sagar@eventuas.dev");
	}

	@Test
	void updatesUserDetailsWhenClaimsChange() throws Exception {
		UUID userId = UUID.randomUUID();
		var oldClaims = new TestUser(userId, "Old Name", "old@eventuas.dev");
		var newClaims = new TestUser(userId, "New Name", "new@eventuas.dev");

		mvc.perform(get("/users/me")
				.with(oldClaims.jwtPostProcessor()))
			.andExpect(status().isOk());

		mvc.perform(get("/users/me")
				.with(newClaims.jwtPostProcessor()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("New Name"))
			.andExpect(jsonPath("$.email").value("new@eventuas.dev"));
	}

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		mvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized());
	}
}
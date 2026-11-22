package eventuas.user;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudRepository<User, UUID> {

	@Query("""
		INSERT INTO users (id, name, email)
		VALUES (:id, :name, :email)
		ON CONFLICT (id) DO UPDATE SET
			name = EXCLUDED.name,
			email = EXCLUDED.email,
			updated_at = CASE
				WHEN users.name IS DISTINCT FROM EXCLUDED.name
					OR users.email IS DISTINCT FROM EXCLUDED.email
				THEN now()
				ELSE users.updated_at
			END
		RETURNING *
	""")
	User upsert(UUID id, String name, String email);

	Optional<User> findByEmailIgnoreCase(String email);
}
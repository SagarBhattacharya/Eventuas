package eventuas.check_in;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("check_ins")
public record CheckIn(
	@Id UUID id,
	UUID ticketId,
	UUID checkedInBy,
	Instant checkedInAt
) {}
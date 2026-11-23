CREATE TABLE idempotency_keys (
	user_id UUID NOT NULL REFERENCES users (id),
	idempotency_key VARCHAR(128) NOT NULL,
	ticket_id UUID REFERENCES tickets (id),
	created_at TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (user_id, idempotency_key)
);
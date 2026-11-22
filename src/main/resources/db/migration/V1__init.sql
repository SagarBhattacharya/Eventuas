CREATE TABLE users (
	id UUID PRIMARY KEY,
	name TEXT NOT NULL,
	email TEXT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
	id UUID PRIMARY KEY DEFAULT uuidv4(),
	name TEXT NOT NULL,
	description TEXT NOT NULL,
	venue TEXT NOT NULL,
	start_date TIMESTAMPTZ NOT NULL,
	end_date TIMESTAMPTZ NOT NULL,
	sales_start_date TIMESTAMPTZ,
	sales_end_date TIMESTAMPTZ,
	organizer_id UUID NOT NULL REFERENCES users(id),
	status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
  	CHECK ( status IN ('DRAFT', 'ACTIVE', 'CLOSED') ),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_organizer_id ON events(organizer_id);

CREATE TABLE ticket_tiers (
	id UUID PRIMARY KEY DEFAULT uuidv4(),
	tier TEXT NOT NULL,
	description TEXT NOT NULL,
	available_qty BIGINT NOT NULL CHECK ( available_qty >= 0 ),
	price BIGINT NOT NULL CHECK ( price >= 0 ),
	currency CHAR(3) NOT NULL CHECK ( currency ~ '^[A-Z]{3}$' ),
	event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
	status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
		CHECK ( status IN ('ACTIVE', 'CLOSED') ),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_tiers_event_id ON ticket_tiers(event_id);
CREATE UNIQUE INDEX uq_active_event_tier
	ON ticket_tiers(event_id, tier)
	WHERE status = 'ACTIVE';

CREATE TABLE tickets (
	id UUID PRIMARY KEY DEFAULT uuidv4(),
	purchased_by UUID NOT NULL REFERENCES users(id),
	tier_id UUID NOT NULL REFERENCES ticket_tiers(id),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_purchased_by ON tickets(purchased_by);
CREATE INDEX idx_tickets_tier_id ON tickets(tier_id);
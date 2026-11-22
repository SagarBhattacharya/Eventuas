CREATE INDEX IF NOT EXISTS idx_users_email ON users (LOWER(email));

CREATE TABLE event_staffs (
	id UUID PRIMARY KEY DEFAULT uuidv4(),
	event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
	user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_staffs_user_id ON event_staffs(user_id);

CREATE TABLE check_ins (
	id UUID PRIMARY KEY DEFAULT uuidv4(),
	ticket_id UUID NOT NULL UNIQUE REFERENCES tickets(id),
	checked_in_by UUID NOT NULL REFERENCES users(id),
	checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_check_ins_checked_in_by ON check_ins(checked_in_by);
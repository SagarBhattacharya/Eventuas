CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Accelerates case-insensitive substring search (ILIKE '%q%') on events
CREATE INDEX IF NOT EXISTS idx_events_name_trgm ON events USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_events_description_trgm ON events USING GIN (description gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_events_venue_trgm ON events USING GIN (venue gin_trgm_ops);

-- Accelerates deterministic keyset/offset scans across all three paginated feeds
CREATE INDEX IF NOT EXISTS idx_events_public_feed
	ON events (status, start_date ASC, id ASC)
	WHERE status <> 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_events_organizer_created
	ON events (organizer_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_tickets_buyer_created
	ON tickets (purchased_by, created_at DESC, id DESC);
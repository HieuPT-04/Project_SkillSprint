CREATE TABLE IF NOT EXISTS community_pinned_items (
    pin_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_id UUID NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    link_url VARCHAR(2048),
    message_id UUID,
    pinned_by VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE community_pinned_items ADD COLUMN IF NOT EXISTS link_url VARCHAR(255);

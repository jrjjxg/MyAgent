alter table thread_memory_snapshots
    add column if not exists pending_historical_messages_json jsonb not null default '[]'::jsonb;

alter table thread_memory_snapshots
    add column if not exists recent_end_message_id varchar(128);

alter table thread_memory_snapshots
    add column if not exists recent_window_size integer not null default 20;

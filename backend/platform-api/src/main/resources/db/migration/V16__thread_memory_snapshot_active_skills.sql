alter table thread_memory_snapshots
    add column if not exists active_skill_ids_json jsonb not null default '[]'::jsonb;

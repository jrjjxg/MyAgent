alter table long_term_memory
    add column if not exists memory_type varchar(32) not null default 'SEMANTIC';

alter table long_term_memory
    add column if not exists canonical_key varchar(255);

update long_term_memory
set memory_type = coalesce(nullif(memory_type, ''), 'SEMANTIC')
where memory_type is null or memory_type = '';

update long_term_memory
set canonical_key = lower(regexp_replace(coalesce(title, ''), '[^a-zA-Z0-9]+', '.', 'g'))
where canonical_key is null or canonical_key = '';

alter table memory_extraction_jobs
    add column if not exists eligible_turn_count integer not null default 1;

create index if not exists idx_long_term_memory_user_status_type_key
    on long_term_memory (user_id, status, memory_type, canonical_key, updated_at desc);

create index if not exists idx_memory_extraction_jobs_thread_status_updated
    on memory_extraction_jobs (user_id, thread_id, extractor_version, status, updated_at desc);

create table if not exists memory_extraction_jobs (
    job_id varchar(64) primary key,
    user_id varchar(64) not null,
    thread_id varchar(64) not null,
    message_id varchar(64) not null,
    extractor_version varchar(64) not null,
    status varchar(32) not null,
    attempt_count integer not null,
    last_error text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);

create unique index if not exists uk_memory_extraction_jobs_user_message_version
    on memory_extraction_jobs (user_id, message_id, extractor_version);

create index if not exists idx_memory_extraction_jobs_status_updated
    on memory_extraction_jobs (status, updated_at desc);

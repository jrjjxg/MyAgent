create table if not exists thread_memory_snapshots (
    thread_id varchar(128) primary key,
    user_id varchar(128) not null,
    summary text not null,
    last_compacted_message_id varchar(128),
    active_draft_id varchar(128),
    active_task_id varchar(128),
    task_stage varchar(128),
    updated_at timestamp not null
);
create index if not exists idx_thread_memory_snapshots_user_updated on thread_memory_snapshots(user_id, updated_at desc);

create table if not exists user_profile_memory (
    user_id varchar(128) primary key,
    display_name varchar(256),
    preferred_language varchar(64),
    preferred_output_styles_json jsonb not null,
    project_tags_json jsonb not null,
    notes text,
    updated_at timestamp not null
);

create table if not exists stable_fact_memory (
    memory_id varchar(128) primary key,
    user_id varchar(128) not null,
    fact_type varchar(128),
    title varchar(512) not null,
    content text not null,
    source_thread_id varchar(128),
    source_task_id varchar(128),
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index if not exists idx_stable_fact_memory_user_status_updated on stable_fact_memory(user_id, status, updated_at desc);

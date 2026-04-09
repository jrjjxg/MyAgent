create table if not exists threads (
    thread_id varchar(128) primary key,
    user_id varchar(128) not null,
    title varchar(512) not null,
    status varchar(64) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists messages (
    message_id varchar(128) primary key,
    user_id varchar(128) not null,
    thread_id varchar(128) not null,
    role varchar(32) not null,
    content text not null,
    interaction_mode varchar(64) not null,
    run_id varchar(128),
    task_id varchar(128),
    created_at timestamp not null
);
create index if not exists idx_messages_thread_created on messages(user_id, thread_id, created_at);

create table if not exists research_drafts (
    draft_id varchar(128) primary key,
    user_id varchar(128) not null,
    thread_id varchar(128) not null,
    status varchar(64) not null,
    title varchar(512) not null,
    brief text not null,
    questions_json jsonb not null,
    ready boolean not null,
    last_user_message_id varchar(128),
    last_assistant_message_id varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);
create index if not exists idx_research_drafts_thread on research_drafts(user_id, thread_id, updated_at desc);

create table if not exists tasks (
    task_id varchar(128) primary key,
    user_id varchar(128) not null,
    thread_id varchar(128) not null,
    agent_id varchar(128) not null,
    kind varchar(64) not null,
    status varchar(64) not null,
    title varchar(512) not null,
    summary text,
    stage varchar(128),
    progress integer,
    linked_draft_id varchar(128),
    result_artifact_id varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);
create index if not exists idx_tasks_thread_updated on tasks(user_id, thread_id, updated_at desc);

create table if not exists run_events (
    event_id bigserial primary key,
    run_id varchar(128) not null,
    user_id varchar(128) not null,
    thread_id varchar(128) not null,
    event_type varchar(128) not null,
    payload_json jsonb,
    created_at timestamp not null
);
create index if not exists idx_run_events_thread_created on run_events(user_id, thread_id, created_at desc);
create index if not exists idx_run_events_run_created on run_events(user_id, thread_id, run_id, created_at asc);

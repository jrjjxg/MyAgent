create table if not exists research_task_snapshots (
    task_id varchar(128) primary key,
    user_id varchar(128) not null,
    thread_id varchar(128) not null,
    phase varchar(128),
    iteration_no integer,
    summary text,
    converged boolean not null default false,
    payload_json jsonb not null default '{}'::jsonb,
    updated_at timestamp not null
);

create index if not exists idx_research_task_snapshots_thread
    on research_task_snapshots(user_id, thread_id, updated_at desc);

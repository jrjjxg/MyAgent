alter table tasks alter column thread_id drop not null;

alter table tasks add column if not exists workspace_id varchar(128);
alter table tasks add column if not exists document_id varchar(128);
alter table tasks add column if not exists attempt_count integer not null default 0;
alter table tasks add column if not exists max_attempts integer not null default 3;
alter table tasks add column if not exists last_error text;
alter table tasks add column if not exists started_at timestamp;
alter table tasks add column if not exists completed_at timestamp;

update tasks t
set workspace_id = th.workspace_id
from threads th
where t.workspace_id is null
  and t.thread_id = th.thread_id;

create index if not exists idx_tasks_workspace_updated on tasks(user_id, workspace_id, updated_at desc);
create unique index if not exists uq_tasks_ingest_document on tasks(user_id, workspace_id, document_id, kind);

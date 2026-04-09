create table if not exists workspaces (
    workspace_id varchar(128) primary key,
    user_id varchar(128) not null,
    title varchar(512) not null,
    status varchar(64) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index if not exists idx_workspaces_user_updated on workspaces(user_id, updated_at desc);

alter table threads add column if not exists workspace_id varchar(128);
update threads
set workspace_id = coalesce(workspace_id, concat('legacy-workspace-', thread_id))
where workspace_id is null;

insert into workspaces (workspace_id, user_id, title, status, created_at, updated_at)
select concat('legacy-workspace-', thread_id),
       user_id,
       title,
       'ACTIVE',
       created_at,
       updated_at
from threads
where not exists (
    select 1
    from workspaces
    where workspaces.workspace_id = concat('legacy-workspace-', threads.thread_id)
);

alter table threads alter column workspace_id set not null;
create index if not exists idx_threads_user_workspace_updated on threads(user_id, workspace_id, updated_at desc);

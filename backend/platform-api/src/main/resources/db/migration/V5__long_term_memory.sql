create table if not exists long_term_memory (
    memory_id varchar(64) primary key,
    user_id varchar(64) not null,
    title varchar(255) not null,
    content text not null,
    source_thread_id varchar(64),
    source_message_id varchar(64),
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_long_term_memory_user_status_updated
    on long_term_memory (user_id, status, updated_at desc);

drop table if exists stable_fact_memory;
drop table if exists user_profile_memory;

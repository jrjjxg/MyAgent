alter table long_term_memory
    add column if not exists source_task_id varchar(64);

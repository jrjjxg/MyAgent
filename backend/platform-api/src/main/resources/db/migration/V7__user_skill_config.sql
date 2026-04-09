create table if not exists user_skill_config (
    config_id varchar(64) primary key,
    user_id varchar(64) not null,
    skill_id varchar(128) not null,
    enabled boolean not null,
    env_ciphertext bytea,
    env_iv bytea,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uk_user_skill_config_user_skill
    on user_skill_config (user_id, skill_id);

create index if not exists idx_user_skill_config_user_updated
    on user_skill_config (user_id, updated_at desc);

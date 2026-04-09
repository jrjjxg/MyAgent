create table if not exists user_model_provider_config (
    config_id varchar(64) primary key,
    user_id varchar(64) not null,
    provider_id varchar(32) not null,
    enabled boolean not null,
    api_key_ciphertext bytea,
    api_key_iv bytea,
    model_override varchar(255),
    base_url_override varchar(512),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uk_user_model_provider_config_user_provider
    on user_model_provider_config (user_id, provider_id);

create index if not exists idx_user_model_provider_config_user_updated
    on user_model_provider_config (user_id, updated_at desc);

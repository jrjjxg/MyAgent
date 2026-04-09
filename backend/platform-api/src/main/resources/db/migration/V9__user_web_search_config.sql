create table if not exists user_web_search_config (
    config_id varchar(64) primary key,
    user_id varchar(128) not null,
    provider_override varchar(32),
    tavily_api_key_ciphertext bytea,
    tavily_api_key_iv bytea,
    search_api_base_url_override varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists idx_user_web_search_config_user_id
    on user_web_search_config (user_id);

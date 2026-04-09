alter table research_drafts
    add column if not exists objective text,
    add column if not exists scope text,
    add column if not exists output_format varchar(256),
    add column if not exists constraints_json jsonb not null default '[]'::jsonb;

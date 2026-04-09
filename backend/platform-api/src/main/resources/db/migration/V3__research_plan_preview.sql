alter table research_drafts
    add column if not exists revision integer not null default 0,
    add column if not exists plan_summary text not null default '',
    add column if not exists plan_steps_json jsonb not null default '[]'::jsonb;

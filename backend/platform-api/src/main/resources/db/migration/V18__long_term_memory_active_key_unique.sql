create unique index if not exists uk_long_term_memory_active_key
    on long_term_memory (user_id, memory_type, canonical_key)
    where status = 'ACTIVE'
      and canonical_key is not null;

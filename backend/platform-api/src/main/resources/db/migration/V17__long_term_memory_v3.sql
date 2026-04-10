alter table long_term_memory
    add column if not exists value_json jsonb;

with ranked as (
    select memory_id,
           row_number() over (
               partition by user_id, memory_type, canonical_key
               order by updated_at desc, created_at desc, memory_id desc
           ) as row_num
    from long_term_memory
    where status = 'ACTIVE'
      and canonical_key is not null
      and canonical_key <> ''
)
update long_term_memory memory
set status = 'DELETED',
    updated_at = now()
from ranked
where memory.memory_id = ranked.memory_id
  and ranked.row_num > 1;

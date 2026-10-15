-- Remove retired Taxi/Auto entities from incremental sync while preserving all Bus data.
delete from public.data_change_log
where entity_type in ('driver', 'seater');

alter table public.data_change_log
  drop constraint if exists data_change_log_entity_type_check;

alter table public.data_change_log
  add constraint data_change_log_entity_type_check
  check (entity_type in ('place', 'route'));

notify pgrst, 'reload schema';

-- Operational template ONLY. Not a Flyway migration and never run by application startup.
-- Roles must be separately approved/created with private credentials. No passwords here.
BEGIN;
DO $$
BEGIN
    IF current_database() = 'bodego_db'
       OR current_setting('bodego.expected_database', true) IS DISTINCT FROM current_database()
       OR current_setting('bodego.staging_grants_approval', true) IS DISTINCT FROM 'staging-grants-reviewed-v1' THEN
        RAISE EXCEPTION 'STAGING_GRANTS_DESTINATION_NOT_APPROVED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='bodego_runtime'
                   AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolbypassrls)
       OR pg_has_role('bodego_runtime','bodego_migrator','MEMBER')
       OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='public' AND r.rolname='bodego_runtime')
       OR EXISTS (SELECT 1 FROM pg_namespace n JOIN pg_roles r ON r.oid=n.nspowner
                  WHERE n.nspname='public' AND r.rolname='bodego_runtime')
       OR EXISTS (SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid=d.datdba
                  WHERE d.datname=current_database() AND r.rolname='bodego_runtime') THEN
        RAISE EXCEPTION 'STAGING_RUNTIME_ROLE_NOT_RESTRICTED';
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO bodego_runtime',current_database());
END $$;
REVOKE CREATE ON SCHEMA public FROM PUBLIC, bodego_runtime;
GRANT USAGE ON SCHEMA public TO bodego_runtime;
GRANT SELECT, INSERT, UPDATE ON public.categories, public.products, public.lots,
    public.correction_requests, public.alerts, public.recommendations, public.offers,
    public.businesses, public.users TO bodego_runtime;
GRANT SELECT, INSERT ON public.audit_logs, public.inventory_movements, public.physical_counts,
    public.stock_adjustments, public.wastes TO bodego_runtime;
-- Physical counts are immutable through HTTP, but locking them requires UPDATE on one column.
GRANT UPDATE (updated_at) ON public.physical_counts TO bodego_runtime;
GRANT USAGE ON SEQUENCE public.alerts_id_seq, public.audit_logs_id_seq, public.categories_id_seq,
    public.correction_requests_id_seq, public.inventory_movements_id_seq, public.lots_id_seq,
    public.offers_id_seq, public.physical_counts_id_seq, public.products_id_seq,
    public.recommendations_id_seq, public.stock_adjustments_id_seq, public.wastes_id_seq,
    public.businesses_id_seq, public.users_id_seq TO bodego_runtime;
-- No DELETE, TRUNCATE, schema CREATE, sequence UPDATE/setval, or Flyway history grants.
-- Review/reapply an explicit list after future migrations; never grant on ALL TABLES.
COMMIT;

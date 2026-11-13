-- Intentionally OUTSIDE db/migration. Requires explicit location and dataset opt-in.
-- Addresses are fictitious test data. Never include this location for other tenants.
DO $adoption$
DECLARE
    source_table TEXT;
    invalid_references BOOLEAN;
BEGIN
    IF '${bodegoAdoption}' <> 'JhorchStore-EthanStore-approved-v1' THEN
        RAISE EXCEPTION 'Explicit authorization for the Bodego local dataset is required';
    END IF;

    -- Keep new business writes out while importing IDs and restarting their identity.
    LOCK TABLE public.businesses IN ACCESS EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM public.businesses WHERE id IN (1, 2)) THEN
        RAISE EXCEPTION 'Historical business IDs already exist; inspect before adoption';
    END IF;

    FOREACH source_table IN ARRAY ARRAY[
        'categories', 'products', 'lots', 'inventory_movements', 'physical_counts',
        'stock_adjustments', 'correction_requests', 'wastes', 'alerts', 'audit_logs',
        'recommendations', 'offers'
    ] LOOP
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM public.%I WHERE business_id IS NULL OR business_id NOT IN (1, 2))',
            source_table
        ) INTO invalid_references;
        IF invalid_references THEN
            RAISE EXCEPTION 'Unexpected historical business reference in %', source_table;
        END IF;
    END LOOP;
END;
$adoption$;

INSERT INTO public.businesses (id, name, address, phone, active, created_at, updated_at)
VALUES
    (1, 'JhorchStore', 'Av. Los Álamos 245, Santiago de Surco, Lima, Perú',
        '985559483', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'EthanStore', 'Jr. Las Palmeras 518, San Miguel, Lima, Perú',
        '917927486', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

DO $identity$
DECLARE
    identity_sequence REGCLASS := pg_get_serial_sequence('public.businesses', 'id')::REGCLASS;
    sequence_last BIGINT;
    sequence_called BOOLEAN;
    sequence_increment BIGINT;
    next_identity BIGINT;
BEGIN
    SELECT seqincrement INTO sequence_increment FROM pg_sequence WHERE seqrelid = identity_sequence;
    IF sequence_increment IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION 'Unexpected Business identity increment';
    END IF;
    EXECUTE format('SELECT last_value, is_called FROM %s', identity_sequence)
        INTO sequence_last, sequence_called;
    SELECT greatest(
        coalesce(max(id), 0) + 1,
        sequence_last + CASE WHEN sequence_called THEN sequence_increment ELSE 0 END
    ) INTO next_identity FROM public.businesses;
    -- RESTART is transactional, unlike setval. Never move an advanced identity backwards.
    EXECUTE format('ALTER SEQUENCE %s RESTART WITH %s', identity_sequence, next_identity);
END;
$identity$;

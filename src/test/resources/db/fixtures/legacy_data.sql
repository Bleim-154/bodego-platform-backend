-- Synthetic data only; every legacy table populated, business IDs 1 and 2 preserved.
INSERT INTO public.categories (id, created_at, updated_at, business_id, name)
VALUES (2, now(), now(), 1, 'Fixture drinks'), (5, now(), now(), 2, 'Fixture food');
INSERT INTO public.products (id, created_at, updated_at, active, business_id, category_id, minimum_stock, name, price)
VALUES (1, now(), now(), true, 1, 2, 5, 'Fixture water', 2.50),
       (2, now(), now(), true, 2, 5, 0, 'Fixture bread', 1.00);
INSERT INTO public.lots (id, created_at, updated_at, batch_number, business_id, product_id, quantity)
VALUES (1, now(), now(), 'FIXTURE-1', 1, 1, 3);
INSERT INTO public.inventory_movements (id, created_at, updated_at, business_id, lot_id, quantity, reason, type)
VALUES (1, now(), now(), 1, 1, 3, 'Fixture entry', 'ENTRY');
INSERT INTO public.physical_counts (id, created_at, updated_at, business_id, counted_at, counted_quantity, difference, lot_id, system_quantity)
VALUES (1, now(), now(), 1, now(), 3, 1, 1, 2);
INSERT INTO public.stock_adjustments (id, created_at, updated_at, adjusted_at, business_id, difference, lot_id, new_quantity, physical_count_id, previous_quantity, reason)
VALUES (1, now(), now(), now(), 1, 1, 1, 3, 1, 2, 'Fixture adjustment');
INSERT INTO public.correction_requests (id, created_at, updated_at, business_id, physical_count_id, reason, requested_at, status)
VALUES (1, now(), now(), 1, 1, 'Fixture correction', now(), 'APPROVED');
INSERT INTO public.wastes (id, created_at, updated_at, business_id, lot_id, quantity, reason, registered_at)
VALUES (1, now(), now(), 1, 1, 1, 'DAMAGED', now());
INSERT INTO public.alerts (id, created_at, updated_at, business_id, generated_at, message, product_id, resolved, severity, type)
VALUES (1, now(), now(), 1, now(), 'Fixture low stock', 1, false, 'LOW', 'LOW_STOCK');
INSERT INTO public.audit_logs (id, created_at, updated_at, action, business_id, description, entity_id, entity_type, occurred_at)
VALUES (1, now(), now(), 'FIXTURE', 1, 'Fixture audit', 1, 'Lot', now());
INSERT INTO public.recommendations (id, created_at, updated_at, business_id, dismissed, generated_at, message, product_id, type)
VALUES (1, now(), now(), 1, false, now(), 'Fixture restock', 1, 'RESTOCK');
INSERT INTO public.offers (id, created_at, updated_at, active, business_id, discount_percentage, end_date, product_id, start_date, title)
VALUES (1, now(), now(), true, 2, 10, '2027-12-31', 2, '2026-01-01', 'Fixture offer');

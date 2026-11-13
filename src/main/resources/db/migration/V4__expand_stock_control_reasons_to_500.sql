-- Preserve complete correction reasons in both the adjustment and its generated movement.
-- Widen existing columns only; PostgreSQL/Flyway execute both changes transactionally.
ALTER TABLE public.inventory_movements
    ALTER COLUMN reason TYPE character varying(500);

ALTER TABLE public.stock_adjustments
    ALTER COLUMN reason TYPE character varying(500);

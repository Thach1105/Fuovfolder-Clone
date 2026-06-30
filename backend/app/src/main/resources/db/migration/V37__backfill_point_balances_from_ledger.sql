-- Seed point_balances from points_ledger for users who have ledger entries
-- but no cached balance row yet. Existing rows are updated to match SUM(delta).

INSERT INTO point_balances (id, user_id, balance_points, created_at, updated_at)
SELECT gen_random_uuid(), pl.user_id, SUM(pl.delta), now(), now()
FROM points_ledger pl
WHERE NOT EXISTS (
    SELECT 1 FROM point_balances pb WHERE pb.user_id = pl.user_id
)
GROUP BY pl.user_id;

UPDATE point_balances pb
SET balance_points = (
    SELECT COALESCE(SUM(pl.delta), 0)
    FROM points_ledger pl
    WHERE pl.user_id = pb.user_id
),
updated_at = now()
WHERE EXISTS (
    SELECT 1 FROM points_ledger pl WHERE pl.user_id = pb.user_id
);

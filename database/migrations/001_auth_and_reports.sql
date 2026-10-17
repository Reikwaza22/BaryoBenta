-- =====================================================================
-- Migration 001 — auth, reports, and rating uniqueness
-- Run this ONCE on the live Supabase database (SQL Editor) BEFORE
-- redeploying the updated API. It does not drop any data.
-- =====================================================================

-- 1. REPORT can now target a user, a listing, or both.
ALTER TABLE report
    ADD COLUMN IF NOT EXISTS reported_user_id INT REFERENCES app_user(user_id) ON DELETE CASCADE;

-- 2. A member may rate a given transaction only once.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'rating_transaction_rater_key') THEN
        ALTER TABLE rating ADD CONSTRAINT rating_transaction_rater_key UNIQUE (transaction_id, rater_id);
    END IF;
END $$;

-- 3. Standardize stored roles to 'Buyer' / 'Seller' / 'Admin'.
UPDATE app_user SET role = 'Buyer'  WHERE lower(role) = 'buyer';
UPDATE app_user SET role = 'Seller' WHERE lower(role) = 'seller';
UPDATE app_user SET role = 'Admin'  WHERE lower(role) = 'admin';

-- 4. Invalidate legacy placeholder password hashes ("hashed:<plain text>").
--    Affected members must register again or be re-seeded; the updated API
--    only stores and accepts bcrypt hashes.
UPDATE app_user
   SET password_hash = 'INVALID_LEGACY_HASH_' || user_id
 WHERE password_hash LIKE 'hashed:%';

-- 5. Demo accounts for evaluation (bcrypt hashes; passwords in README).
--    Safe to re-run: conflicts on the unique email are skipped.
INSERT INTO app_user (first_name, last_name, email, password_hash, contact_number, role, verified_status) VALUES
('Sam', 'Diosa', 'seller@baryobenta.demo', '$2b$12$CLSK2v/uUAEWwmUe4XdM0utlpMrsdgnAuiyT4k25baRtBt3O8ubAC', '09171110001', 'Seller', TRUE),
('Bea', 'Lakas', 'buyer@baryobenta.demo',  '$2b$12$yK9kzDgivfmA/.fAMcRjne7ROvx7i4/Ev21qCAeXKyJA6bkR0Fjh.', '09171110002', 'Buyer',  TRUE),
('Ada', 'Min',   'admin@baryobenta.demo',  '$2b$12$42Z2RJLEZVIYif0HJcNNBuI6wDM88XO721PafvuXtIsCsnjA0YWA2', NULL,          'Admin',  TRUE)
ON CONFLICT (email) DO NOTHING;

-- 6. Seed the standard categories if the table is empty.
INSERT INTO category (category_name)
SELECT * FROM (VALUES
    ('Electronics'), ('Books & School Supplies'), ('Furniture'),
    ('Clothing'), ('Sports & Outdoors'), ('Home & Garden'),
    ('Vehicles & Parts'), ('Others')
) AS seed(category_name)
WHERE NOT EXISTS (SELECT 1 FROM category)
ON CONFLICT (category_name) DO NOTHING;

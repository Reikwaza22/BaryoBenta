-- =====================================================================
-- Migration 003 - demo accounts for the mobile (PHP-compatible) contract
--
-- Same three accounts documented in README.md, mirrored into the mobile
-- contract tables so the Android app can log in with the published demo
-- credentials. Passwords are bcrypt hashes; plain text never ships here.
--
-- Demo listings, photos and the sample conversation are created through
-- the API itself by database/seed_demo_content.sh (photos are stored as
-- blobs, so they cannot be inserted from SQL).
-- =====================================================================

INSERT INTO users (full_name, username, email, password_hash, role, location, is_verified, credits) VALUES
 ('Rosa Dela Cruz',  'baryoseller', 'seller@baryobenta.demo', '$2b$12$OMCMFRIkJFeHwF00fI41Zu78L3UF/88HxEuUAcXgiBJ3rRr9Ob1E6', 'user',  'Talisay Campus Community', TRUE,  500),
 ('Juan Mabini',     'baryobuyer',  'buyer@baryobenta.demo',  '$2b$12$7XbZTihleLkEzJWS6q0YdelcMjaNf9CQ/pwlyTi.YE9R/DkKw8pWW', 'user',  'BSIT2 Campus Community',   FALSE, 200),
 ('BaryoBenta Admin','baryoadmin',  'admin@baryobenta.demo',  '$2b$12$vx4vuNsHPaURTZLxtlUrQ.v8E4TWH5NdCN3bGuVK0vYKnqODGu34G', 'admin', 'SWU Campus',               TRUE,  0)
ON CONFLICT (email) DO NOTHING;

-- Record the opening balances in the wallet ledger.
INSERT INTO credit_ledger (user_id, amount, kind, note)
SELECT u.user_id, u.credits, 'admin_credit', 'Opening demo balance'
FROM users u
WHERE u.email IN ('seller@baryobenta.demo', 'buyer@baryobenta.demo')
  AND u.credits > 0
  AND NOT EXISTS (SELECT 1 FROM credit_ledger l WHERE l.user_id = u.user_id);

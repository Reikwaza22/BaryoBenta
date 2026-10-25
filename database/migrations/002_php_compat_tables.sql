-- =====================================================================
-- Migration 002 - mobile (PHP-compatible) contract tables
--
-- The Android client in android/ calls the team's original PHP endpoints
-- (login.php, listings.php, messages.php, ...). api/php_compat.py serves
-- that same contract from the deployed FastAPI app, and it reads/writes the
-- tables created here. They deliberately keep the plural names of the local
-- XAMPP schema so the SQL is a faithful port, and they do not collide with
-- the REST API tables (app_user, listing, message, ...).
--
-- Non-destructive: safe to re-run. Execute in Supabase > SQL Editor.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    user_id       SERIAL PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin')),
    location      VARCHAR(120) NOT NULL DEFAULT 'BSIT2 Campus Community',
    is_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_banned     BOOLEAN      NOT NULL DEFAULT FALSE,
    api_token     VARCHAR(64)  UNIQUE,
    credits       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (credits >= 0),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Digital wallet history; users.credits is the running balance.
CREATE TABLE IF NOT EXISTS credit_ledger (
    ledger_id  SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    amount     NUMERIC(12,2) NOT NULL,
    kind       VARCHAR(20) NOT NULL CHECK (kind IN ('topup','commission','posting_fee','admin_credit','admin_debit')),
    note       VARCHAR(200) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Manual top-ups stay pending until an administrator verifies the payment.
CREATE TABLE IF NOT EXISTS topup_requests (
    request_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 50 AND amount <= 10000),
    payment_method VARCHAR(20) NOT NULL DEFAULT 'GCash' CHECK (payment_method IN ('GCash','PayMaya','Bank transfer')),
    payment_reference VARCHAR(120) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewed_by INT REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS kyc_requests (
    kyc_id      SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    id_type     VARCHAR(60) NOT NULL,
    id_number   VARCHAR(60) NOT NULL,
    id_photo    VARCHAR(255) NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
    category_id SERIAL PRIMARY KEY,
    name        VARCHAR(60) NOT NULL UNIQUE,
    icon_key    VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS listings (
    listing_id  SERIAL PRIMARY KEY,
    seller_id   INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    category_id INT NOT NULL REFERENCES categories(category_id),
    title       VARCHAR(120) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    photo       VARCHAR(255),
    status      VARCHAR(10) NOT NULL DEFAULT 'available' CHECK (status IN ('available','pending','sold')),
    is_removed  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS listing_specs (
    spec_id    SERIAL PRIMARY KEY,
    listing_id INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    spec_name  VARCHAR(60) NOT NULL,
    spec_value VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS favorites (
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    listing_id INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, listing_id)
);

CREATE TABLE IF NOT EXISTS comments (
    comment_id SERIAL PRIMARY KEY,
    listing_id INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    body       VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS messages (
    message_id       SERIAL PRIMARY KEY,
    listing_id       INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    sender_id        INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    receiver_id      INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    body             VARCHAR(1000) NOT NULL DEFAULT '',
    type             VARCHAR(10) NOT NULL DEFAULT 'text' CHECK (type IN ('text','photo','voice')),
    photo            VARCHAR(255),
    duration         INT NOT NULL DEFAULT 0,
    reaction         VARCHAR(16) NOT NULL DEFAULT '',
    reaction_user_id INT REFERENCES users(user_id) ON DELETE SET NULL,
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id SERIAL PRIMARY KEY,
    listing_id     INT NOT NULL UNIQUE REFERENCES listings(listing_id) ON DELETE CASCADE,
    buyer_id       INT NOT NULL REFERENCES users(user_id),
    amount         NUMERIC(12,2) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reviews (
    review_id   SERIAL PRIMARY KEY,
    listing_id  INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    reviewer_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    seller_id   INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(500) NOT NULL DEFAULT '',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (listing_id, reviewer_id)
);

CREATE TABLE IF NOT EXISTS reports (
    report_id   SERIAL PRIMARY KEY,
    listing_id  INT NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    reporter_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    reason      VARCHAR(30) NOT NULL,
    details     VARCHAR(500) NOT NULL DEFAULT '',
    status      VARCHAR(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open','dismissed','actioned')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (listing_id, reporter_id)
);

-- Serverless deployments have no writable file system, so photos, chat images,
-- voice notes and KYC scans uploaded through the mobile contract are stored
-- here and streamed back by GET /uploads/{name}.
CREATE TABLE IF NOT EXISTS media_blob (
    name         VARCHAR(255) PRIMARY KEY,
    content_type VARCHAR(80) NOT NULL DEFAULT 'application/octet-stream',
    data         BYTEA NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_listings_cat    ON listings(category_id);
CREATE INDEX IF NOT EXISTS idx_listings_seller ON listings(seller_id);
CREATE INDEX IF NOT EXISTS idx_messages_pair   ON messages(listing_id, sender_id, receiver_id);
CREATE INDEX IF NOT EXISTS idx_ledger_user     ON credit_ledger(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_pending_topup_per_user ON topup_requests(user_id) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_topup_requests_pending ON topup_requests(status, created_at);

INSERT INTO categories (name, icon_key) VALUES
 ('Electronics','electronics'),
 ('Fashion & Accessories','fashion'),
 ('Home & Living','home'),
 ('Sports & Outdoors','sports'),
 ('Vehicles','vehicles'),
 ('Others','others')
ON CONFLICT (name) DO NOTHING;

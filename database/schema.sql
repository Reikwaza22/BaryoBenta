-- =====================================================================
-- BaryoBenta — PostgreSQL database script (3NF schema + sample records)
-- Group SWU-10 · ITE298 Final Project
--
-- How to run (fresh database):
--   psql "$DATABASE_URL" -f database/schema.sql
--   or in pgAdmin / Supabase SQL Editor: paste and run.
--
-- WARNING: the DROP statements below recreate every BaryoBenta table and
-- delete existing data. On the live Supabase database, run
-- database/migrations/001_auth_and_reports.sql instead.
-- =====================================================================

DROP TABLE IF EXISTS report        CASCADE;
DROP TABLE IF EXISTS boost         CASCADE;
DROP TABLE IF EXISTS rating        CASCADE;
DROP TABLE IF EXISTS message       CASCADE;
DROP TABLE IF EXISTS favorite      CASCADE;
DROP TABLE IF EXISTS "transaction" CASCADE;
DROP TABLE IF EXISTS listing_image CASCADE;
DROP TABLE IF EXISTS listing       CASCADE;
DROP TABLE IF EXISTS category      CASCADE;
DROP TABLE IF EXISTS app_user      CASCADE;

-- --------------------------------------------------------------- app_user
CREATE TABLE app_user (
    user_id         SERIAL PRIMARY KEY,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,             -- bcrypt hash, never plain text
    contact_number  VARCHAR(20),
    role            VARCHAR(20)  NOT NULL DEFAULT 'Buyer'
                    CHECK (role IN ('Buyer', 'Seller', 'Admin')),
    verified_status BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------------- category
CREATE TABLE category (
    category_id   SERIAL PRIMARY KEY,
    category_name VARCHAR(80) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------- listing
CREATE TABLE listing (
    listing_id  SERIAL PRIMARY KEY,
    seller_id   INT           NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    category_id INT           NOT NULL REFERENCES category(category_id),
    title       VARCHAR(150)  NOT NULL,
    description TEXT,
    price       NUMERIC(10,2) NOT NULL CHECK (price >= 0),   -- 0 = free item
    status      VARCHAR(20)   NOT NULL DEFAULT 'Available'
                CHECK (status IN ('Available', 'Sold', 'Removed')),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_listing_seller   ON listing(seller_id);
CREATE INDEX idx_listing_category ON listing(category_id);
CREATE INDEX idx_listing_status   ON listing(status);

-- ---------------------------------------------------------- listing_image
CREATE TABLE listing_image (
    image_id      SERIAL PRIMARY KEY,
    listing_id    INT          NOT NULL REFERENCES listing(listing_id) ON DELETE CASCADE,
    image_url     VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0
);

-- ------------------------------------------------------------ transaction
CREATE TABLE "transaction" (
    transaction_id  SERIAL PRIMARY KEY,
    listing_id      INT        NOT NULL REFERENCES listing(listing_id),
    buyer_id        INT        NOT NULL REFERENCES app_user(user_id),
    meetup_location VARCHAR(200),
    meetup_datetime TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'Pending'
                    CHECK (status IN ('Pending', 'Completed', 'Cancelled')),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------------- favorite
CREATE TABLE favorite (
    user_id    INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    listing_id INT NOT NULL REFERENCES listing(listing_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, listing_id)
);

-- ---------------------------------------------------------------- message
CREATE TABLE message (
    message_id   SERIAL PRIMARY KEY,
    sender_id    INT  NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    receiver_id  INT  NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    listing_id   INT  REFERENCES listing(listing_id) ON DELETE SET NULL,
    message_text TEXT NOT NULL,
    sent_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_message_conversation ON message(sender_id, receiver_id, sent_at);

-- ----------------------------------------------------------------- rating
CREATE TABLE rating (
    rating_id      SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL REFERENCES "transaction"(transaction_id) ON DELETE CASCADE,
    rater_id       INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    ratee_id       INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    score          SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    -- a member may rate a given transaction only once
    CONSTRAINT rating_transaction_rater_key UNIQUE (transaction_id, rater_id)
);

-- ------------------------------------------------------------------ boost
-- The boost fee is settled offline (cash / GCash to the administrator);
-- the application never processes payment. payment_status starts 'Pending'
-- and an administrator sets 'Confirmed' after the offline payment or
-- 'Rejected' otherwise.
CREATE TABLE boost (
    boost_id       SERIAL PRIMARY KEY,
    listing_id     INT           NOT NULL REFERENCES listing(listing_id) ON DELETE CASCADE,
    amount         NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    start_date     TIMESTAMP,
    end_date       TIMESTAMP,
    payment_status VARCHAR(20)   NOT NULL DEFAULT 'Pending'
                   CHECK (payment_status IN ('Pending', 'Confirmed', 'Rejected'))
);

-- ----------------------------------------------------------------- report
-- A report targets a listing, a user, or both; the API requires at least
-- one of listing_id / reported_user_id.
CREATE TABLE report (
    report_id        SERIAL PRIMARY KEY,
    reporter_id      INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    listing_id       INT REFERENCES listing(listing_id) ON DELETE CASCADE,
    reported_user_id INT REFERENCES app_user(user_id) ON DELETE CASCADE,
    reason           VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'Open'
                     CHECK (status IN ('Open', 'Resolved', 'Dismissed')),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =====================================================================
-- Sample records
-- Demo accounts (for evaluation only — documented in the README):
--   seller@baryobenta.demo / sellerpass1   (Seller)
--   buyer@baryobenta.demo  / buyerpass1    (Buyer)
--   admin@baryobenta.demo  / adminpass1    (Admin)
-- =====================================================================

INSERT INTO app_user (first_name, last_name, email, password_hash, contact_number, role, verified_status) VALUES
('Sam',   'Diosa',  'seller@baryobenta.demo', '$2b$12$CLSK2v/uUAEWwmUe4XdM0utlpMrsdgnAuiyT4k25baRtBt3O8ubAC', '09171110001', 'Seller', TRUE),
('Bea',   'Lakas',  'buyer@baryobenta.demo',  '$2b$12$yK9kzDgivfmA/.fAMcRjne7ROvx7i4/Ev21qCAeXKyJA6bkR0Fjh.', '09171110002', 'Buyer',  TRUE),
('Ada',   'Min',    'admin@baryobenta.demo',  '$2b$12$42Z2RJLEZVIYif0HJcNNBuI6wDM88XO721PafvuXtIsCsnjA0YWA2', NULL,          'Admin',  TRUE);

INSERT INTO category (category_name) VALUES
('Electronics'), ('Books & School Supplies'), ('Furniture'),
('Clothing'), ('Sports & Outdoors'), ('Home & Garden'),
('Vehicles & Parts'), ('Others');

INSERT INTO listing (seller_id, category_id, title, description, price, status) VALUES
(1, 1, 'TI-84 Plus Graphing Calculator', 'Used for one semester, works perfectly. Includes cover.', 850.00, 'Available'),
(1, 2, 'Introduction to Computing textbook', 'Good condition, no highlights.', 300.00, 'Available'),
(2, 4, 'UNIQLO jacket (size M)', 'Barely worn, selling cheap for a quick meetup sale.', 0.00, 'Available');

INSERT INTO listing_image (listing_id, image_url, display_order) VALUES
(1, 'https://baryobenta.vercel.app/static/sample/ti84-front.jpg', 0),
(1, 'https://baryobenta.vercel.app/static/sample/ti84-back.jpg', 1);

INSERT INTO "transaction" (listing_id, buyer_id, meetup_location, meetup_datetime, status) VALUES
(2, 2, 'SWU Library entrance', '2026-10-15 15:00:00', 'Completed');

INSERT INTO favorite (user_id, listing_id) VALUES (2, 1);

INSERT INTO message (sender_id, receiver_id, listing_id, message_text) VALUES
(2, 1, 1, 'Hi! Is the calculator still available? Can we meet at the library tomorrow?'),
(1, 2, 1, 'Yes, still available. Tomorrow 3 PM at the library entrance works for me.');

INSERT INTO rating (transaction_id, rater_id, ratee_id, score, comment) VALUES
(1, 2, 1, 5, 'Smooth meetup, item was exactly as described.');

INSERT INTO boost (listing_id, amount, start_date, end_date, payment_status) VALUES
(1, 50.00, '2026-10-18 00:00:00', '2026-10-25 00:00:00', 'Pending');

INSERT INTO report (reporter_id, listing_id, reported_user_id, reason, status) VALUES
(2, 1, 1, 'Sample report: suspected overpriced duplicate of another listing.', 'Open');

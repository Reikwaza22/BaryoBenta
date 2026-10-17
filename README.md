# BaryoBenta — Community Buy & Sell Marketplace

**Group SWU-10 · ITE298 Final Project**

BaryoBenta is a community buy-and-sell marketplace for one campus or barangay.
Members post items for sale, browse and search listings, save favorites, chat
to arrange a face-to-face meetup, and pay in cash. The app itself never
processes payments. Administrators moderate listings and review reports and
listing-boost requests.

## Group Members

| Member | Role in the project |
| --- | --- |
| Fernandez, Cyril B. | Project lead · FastAPI backend · deployment (Vercel / Supabase) |
| Figueroa, Anthony Nolan G. | Android client (Java, Retrofit) · UI screens |
| Sepra, R Xander Emmanuel M. | Database design (PostgreSQL) · normalization · SQL scripts |
| Yaun, Donna Mae V. | Documentation · use cases · UML/ERD diagrams · testing |

## Live Links

- **Deployed API:** https://baryo-benta.vercel.app
- **Interactive docs (Swagger UI):** https://baryo-benta.vercel.app/docs
- **Health check:** https://baryo-benta.vercel.app/health

## Repository Structure

```
api/                  FastAPI backend (deployed to Vercel)
  main.py             Endpoints and business rules (Vercel entrypoint)
  auth.py             bcrypt password hashing + JWT bearer tokens
  models.py           SQLAlchemy models (one per database table)
  schemas.py          Pydantic request/response schemas and validation
  database.py         DB connection (reads DATABASE_URL from env)
android/              Android mobile client (Java, Gradle, Retrofit)
database/
  schema.sql          Full CREATE TABLE script + sample records (fresh DB)
  migrations/         SQL migrations for the live Supabase database
requirements.txt      Pinned Python dependencies of the API
.env.example          Environment variable template (no real secrets)
```

## Demo Accounts

Seeded by `database/schema.sql` / migration 001 so the protected endpoints can
be evaluated. Log in via `POST /auth/login` and use the returned
`access_token` as `Authorization: Bearer <token>`.

| Email | Password | Role |
| --- | --- | --- |
| `seller@baryobenta.demo` | `sellerpass1` | Seller |
| `buyer@baryobenta.demo` | `buyerpass1` | Buyer |
| `admin@baryobenta.demo` | `adminpass1` | Admin |

Passwords are stored only as bcrypt hashes; plain-text passwords never touch
the database.

## API Setup (local)

Requires Python 3.12+.

```bash
pip install -r requirements.txt
cp .env.example .env        # then fill in DATABASE_URL and SECRET_KEY
uvicorn api.main:app --reload
```

Open http://127.0.0.1:8000/docs for the interactive Swagger UI.

### Database

Fresh local PostgreSQL:

```bash
createdb baryobenta
psql -d baryobenta -f database/schema.sql
```

The live Supabase database was migrated with
`database/migrations/001_auth_and_reports.sql` (adds `report.reported_user_id`,
the one-rating-per-transaction constraint, and the demo accounts).

## Android Setup

Requires Android Studio (Giraffe or newer), Android SDK 34, and a device or
emulator with minSdk 24.

1. Open the `android/` folder in Android Studio and let Gradle sync.
2. The API base URL is configured in
   `android/app/src/main/java/com/baryobenta/community/RetrofitClient.java`
   (`BASE_URL` — defaults to the deployed Vercel API; point it to
   `http://10.0.2.2:8000/` for the emulator against a local `uvicorn`).
3. Run the `app` configuration on your device or emulator.

## Deployment (Vercel + Supabase)

1. Push this repository to GitHub (never commit `.env`).
2. On vercel.com → **Add New → Project** → import the repo. Framework preset:
   **Other**; no build/output settings.
3. Set the environment variables `DATABASE_URL` (Supabase session-pooler
   connection string) and `SECRET_KEY` (long random string used to sign JWTs).
4. Deploy — every push to `main` redeploys automatically.

## Endpoints

Full, always-current documentation: https://baryo-benta.vercel.app/docs

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/users` | — | Register a Buyer or Seller account (UC-01) |
| POST | `/auth/login` | — | Log in, returns a JWT bearer token (UC-04) |
| GET | `/users/{user_id}` | — | Public profile |
| GET | `/users/{user_id}/ratings` | — | Ratings received by a member |
| GET | `/categories` | — | List categories (UC-02) |
| POST | `/categories` | — | Create a category (seeding) |
| GET | `/categories/export/csv` | — | Listings-per-category CSV summary |
| POST | `/listings` | Bearer | Post a listing (UC-02) |
| GET | `/listings` | — | Browse/search listings (`search`, `category_id`) |
| GET | `/listings/{listing_id}` | — | Listing details |
| PATCH | `/listings/{listing_id}` | Bearer (owner) | Edit a listing |
| DELETE | `/listings/{listing_id}` | Bearer (owner/admin) | Remove a listing |
| GET | `/listings/{listing_id}/images` | — | Listing images |
| POST | `/listings/{listing_id}/images` | Bearer (owner) | Attach an image (max 8) |
| POST | `/transactions` | Bearer (buyer) | Record a meetup — status Pending (UC-03) |
| GET | `/transactions` | Bearer | My transactions (as buyer or seller) |
| PATCH | `/transactions/{id}/complete` | Bearer (seller) | Pending → Completed, listing → Sold (UC-03) |
| PATCH | `/transactions/{id}/cancel` | Bearer (buyer/seller) | Pending → Cancelled, listing stays Available |
| POST | `/favorites` | Bearer | Save a favorite listing |
| GET | `/favorites` | Bearer | My favorites |
| DELETE | `/favorites/{listing_id}` | Bearer | Remove a favorite |
| POST | `/messages` | Bearer | Send a chat message (UC-03) |
| GET | `/messages/{peer_id}` | Bearer | Conversation with one member |
| POST | `/ratings` | Bearer (participant) | Rate the other party once per completed transaction |
| POST | `/boosts` | Bearer (owner) | Request a listing boost (fee settled offline) |
| GET | `/boosts` | Bearer | My boost requests (admin: all) |
| PATCH | `/boosts/{boost_id}` | Bearer (admin) | Confirm/Reject after offline payment |
| POST | `/reports` | Bearer | Report a listing and/or a user |
| GET | `/reports` | Bearer (admin) | List reports |
| PATCH | `/reports/{report_id}` | Bearer (admin) | Resolve/Dismiss a report |
| GET | `/health` | — | Health check |

## Security Notes

- Passwords are hashed with **bcrypt** (`passlib`); the API rejects plain-text
  storage and legacy `hashed:` placeholders are invalidated by migration 001.
- Protected endpoints require a **JWT bearer token** issued by
  `POST /auth/login` and signed with the server-side `SECRET_KEY`.
- Registration only accepts the `Buyer` and `Seller` roles; administrator
  accounts are created directly in the database by the team.
- `.env` is gitignored; `.env.example` contains placeholders only.

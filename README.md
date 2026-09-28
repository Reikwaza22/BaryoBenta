# BaryoBenta API

FastAPI backend for the BaryoBenta community buy & sell marketplace, deployed
to Vercel as a serverless function and connected to Supabase PostgreSQL.

## Project structure

```
api/
  main.py       # FastAPI app object (entrypoint Vercel imports)
  database.py   # DB connection, reads DATABASE_URL from env
  models.py     # SQLAlchemy models
  schemas.py    # Pydantic request/response schemas
requirements.txt
.gitignore
```

## Local setup

```bash
pip install -r requirements.txt
```

Create a `.env` file in the project root (never commit this — it's in
`.gitignore`):

```
DATABASE_URL=postgresql://<user>:<password>@<host>:6543/postgres?sslmode=require
```

Run it:

```bash
uvicorn api.main:app --reload
```

Visit `http://127.0.0.1:8000/docs` for interactive Swagger docs.

## Deploy to Vercel

1. Push this repo to a **public** GitHub repository (do not include `.env`).
2. On vercel.com → **Add New → Project** → import the repo.
3. Framework preset: **Other**. Leave build/output settings empty.
4. Under **Environment Variables**, add `DATABASE_URL` with the same
   connection string used locally.
5. Deploy. The app goes live at `<project>.vercel.app`.

## Endpoints

Includes standard CRUD for Users, Categories, Listings, Transactions,
Favorites, and Reviews — see `/docs` for the full list.

### CSV export

```
GET /categories/export/csv
```

Returns a downloadable CSV summarizing the number of listings per category
(`category_name`, `total_listings`), using `COUNT()` + `GROUP BY` with a
`LEFT JOIN` so empty categories still show up as 0.

## What's implemented

- Input validation & sanitization via Pydantic (`schemas.py`)
- Centralized structured error handling (404 / 400 / 422 / 500)
- CSV export endpoint following the serverless pattern: query → build in
  memory with `csv.writer` + `StringIO` → return as a `Response` with
  download headers (no file ever touches disk, since Vercel's filesystem
  is read-only outside `/tmp`)

## Still a draft

- Password hashing uses a placeholder — swap in `passlib[bcrypt]` before
  this goes anywhere near production.
- No auth/session tokens yet.

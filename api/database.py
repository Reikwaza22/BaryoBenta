"""
Database connection setup.

DATABASE_URL is read from the environment (set in Vercel's dashboard,
never committed to Git). Example, for the Supabase pooler:

postgresql://<user>:<password>@<host>:6543/postgres?sslmode=require
"""
import os
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from sqlalchemy.pool import NullPool

load_dotenv()  # loads .env for local dev; ignored on Vercel (env vars set in dashboard)

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://postgres:password@localhost:5432/baryobenta",
)
# Force the psycopg2 driver explicitly even if DATABASE_URL is given as a
# plain "postgresql://" string, so the DB driver never depends on which
# Postgres adapter happens to be importable in the environment.
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg2://", 1)

# NullPool: each serverless cold start opens its own short-lived
# connection instead of holding a pool open, so we don't exhaust the
# Supabase pooler's connection limit across many cold starts.
engine = create_engine(DATABASE_URL, pool_pre_ping=True, poolclass=NullPool)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    """FastAPI dependency that yields a DB session and always closes it."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

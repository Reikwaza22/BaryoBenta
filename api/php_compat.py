"""PHP-compatible endpoints for the BaryoBenta Android client.

The Android app in ``android/`` was written against the team's original PHP API
(``register.php``, ``listings.php``, ``messages.php``, ...).  That API speaks a
very specific contract:

* every response is an envelope ``{"success": bool, "message": str, "data": ...}``
* the session token travels in the ``X-Auth-Token`` header (not ``Authorization``)
* requests are form-encoded or multipart, and the JSON field names match the
  PostgreSQL columns of the PHP schema (``body``, ``photo_url``, ``is_mine``, ...)

This module re-implements that contract on top of the cloud FastAPI backend so
one deployed service (https://baryo-benta.vercel.app) can serve both the
documented REST API in ``main.py`` and the mobile app.  The REST API keeps its
own tables (``app_user``, ``listing``, ``message``, ...); the mobile contract
uses the plural table names of ``database/migrations/002_php_compat_tables.sql``
(``users``, ``listings``, ``messages``, ...).

Uploaded media (listing photos, chat photos, voice notes, KYC scans) cannot be
kept on a serverless file system, so the bytes are stored in the ``media_blob``
table and streamed back from ``GET /uploads/{name}``.
"""
from __future__ import annotations

import functools
import os
import re
import secrets
import urllib.request
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse, Response
from passlib.context import CryptContext
from sqlalchemy import text

from database import engine

router = APIRouter(tags=["Mobile app (PHP-compatible contract)"])

# ---------------------------------------------------------------- configuration
DEBUG = os.getenv("PHP_COMPAT_DEBUG", "0").lower() in ("1", "true", "yes")

# Sellers pay a flat posting fee in digital credits; top-ups are reviewed by an
# administrator (same constants as the local PHP config.php).
CREDIT_POSTING_FEE = 10
TOPUP_MIN = 50
TOPUP_MAX = 10000
TOPUP_PAYMENT_INSTRUCTIONS = (
    "Pay using the BaryoBenta administrator payment details for GCash, PayMaya, "
    "or bank transfer. Enter your transaction reference below. Credits are added "
    "only after payment is verified."
)
TOPUP_METHODS = ("GCash", "PayMaya", "Bank transfer")
REACTIONS = ("", "\U0001F44D", "\u2764\uFE0F", "\U0001F602", "\U0001F62E",
             "\U0001F622", "\U0001F64F", "\U0001F525")
REPORT_REASONS = ("prohibited", "scam", "inappropriate", "other")
MAX_UPLOAD_BYTES = 8 * 1024 * 1024
MAX_MESSAGE_LENGTH = 1000
USERNAME_RE = re.compile(r"^[A-Za-z0-9_.]{3,30}$")
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

# Optional: when running next to a local XAMPP install, existing photo files can
# be served straight from its uploads folder instead of the media_blob table.
LOCAL_UPLOAD_DIR = os.getenv("PHP_UPLOAD_DIR", "")

_pwd = CryptContext(schemes=["bcrypt"], deprecated="auto")

LISTING_SELECT = """
    SELECT l.listing_id, l.seller_id, l.title, l.description, l.price, l.photo, l.status, l.created_at,
           c.category_id, c.name AS category,
           u.full_name AS seller, u.is_verified AS seller_verified,
           COALESCE(r.avg_rating, 0) AS rating, COALESCE(r.cnt, 0) AS review_count,
           EXISTS (SELECT 1 FROM favorites f WHERE f.user_id = :me AND f.listing_id = l.listing_id) AS is_favorite
    FROM listings l
    JOIN categories c ON c.category_id = l.category_id
    JOIN users u ON u.user_id = l.seller_id
    LEFT JOIN (SELECT seller_id, ROUND(AVG(rating)::numeric, 1) AS avg_rating, COUNT(*) AS cnt
               FROM reviews GROUP BY seller_id) r ON r.seller_id = l.seller_id
"""


# -------------------------------------------------------------------- responses
class Fail(Exception):
    """A business-rule rejection, rendered as a PHP-style error envelope."""

    def __init__(self, message: str, code: int = 400) -> None:
        super().__init__(message)
        self.message = message
        self.code = code


def _envelope(success: bool, message: str, data: Any, code: int) -> JSONResponse:
    return JSONResponse(status_code=code,
                        content={"success": success, "message": message, "data": data})


def _ok(data: Any = None, message: str = "OK") -> JSONResponse:
    return _envelope(True, message, data, 200)


def php_endpoint(func):
    """Runs an endpoint and turns exceptions into the PHP error envelope."""

    @functools.wraps(func)
    async def wrapper(*args, **kwargs):
        try:
            return await func(*args, **kwargs)
        except Fail as failure:
            return _envelope(False, failure.message, None, failure.code)
        except Exception as exc:  # mirrors PHP's set_exception_handler in db.php
            detail = f"Server error: {exc}" if DEBUG else "Server error"
            return _envelope(False, detail, None, 500)

    return wrapper


# ----------------------------------------------------------------------- params
async def _params(request: Request) -> tuple[dict[str, str], dict[str, Any]]:
    """Merges query string and form/multipart body (form wins, like $_POST)."""
    values: dict[str, str] = {k: v for k, v in request.query_params.items()}
    files: dict[str, Any] = {}
    if request.method in ("POST", "PUT", "PATCH"):
        content_type = (request.headers.get("content-type") or "").lower()
        if "application/x-www-form-urlencoded" in content_type or "multipart/form-data" in content_type:
            form = await request.form()
            for key, value in form.items():
                # Duck-typed: starlette and fastapi have not always shared the
                # same UploadFile class, and an isinstance() miss silently drops
                # every uploaded photo / voice note.
                if hasattr(value, "read") and hasattr(value, "filename"):
                    files[key] = value
                elif isinstance(value, str):
                    values[key] = value
    return values, files


def _p(values: dict[str, str], key: str, default: str = "") -> str:
    value = values.get(key, default)
    return value.strip() if isinstance(value, str) else default


def _i(values: dict[str, str], key: str, default: int = 0) -> int:
    try:
        return int(float(_p(values, key)))
    except (TypeError, ValueError):
        return default


# --------------------------------------------------------------------- formatting
def _ts(value: Any) -> str | None:
    """PostgreSQL timestamp -> 'YYYY-MM-DD HH:MM:SS' (UTC), as the app expects."""
    if value is None:
        return None
    if isinstance(value, datetime):
        if value.tzinfo is not None:
            value = value.astimezone(timezone.utc).replace(tzinfo=None)
        return value.strftime("%Y-%m-%d %H:%M:%S")
    return str(value)


def _f(value: Any) -> float:
    return float(value) if value is not None else 0.0


def _base_url(request: Request) -> str:
    proto = (request.headers.get("x-forwarded-proto") or request.url.scheme or "https").split(",")[0].strip()
    host = request.headers.get("x-forwarded-host") or request.headers.get("host") or request.url.netloc
    return f"{proto}://{host}/"


def _media_url(request: Request, path: str | None) -> str | None:
    if not path:
        return None
    if path.startswith("http://") or path.startswith("https://"):
        return path
    return _base_url(request) + "uploads/" + path.lstrip("/")


# Fixed rather than derived from the request headers so the warm-up below can
# never be pointed at another host through a spoofed Host / X-Forwarded-Host.
PUBLIC_BASE_URL = (os.getenv("PUBLIC_BASE_URL") or "https://baryo-benta.vercel.app").rstrip("/") + "/"
WARM_TIMEOUT_SECONDS = 6


def _warm_upload(name: str | None) -> None:
    """Requests a freshly stored upload once so Vercel caches it at the edge.

    A newly created listing has an image URL nobody has ever requested, so the
    phone's first load has to cold-start the function and reach PostgreSQL
    before a single byte comes back.  That often outlasts the client's image
    timeout, which leaves the card showing the placeholder until the screen is
    reopened.  Fetching it here - after the row is committed - means the edge
    already has the bytes cached by the time the feed reloads.  Best effort:
    a slow cache warmer must never fail the listing that was just created.
    """
    if not name:
        return
    url = PUBLIC_BASE_URL + "uploads/" + name.lstrip("/")
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "BaryoBenta-warmup/1.0"})
        with urllib.request.urlopen(request, timeout=WARM_TIMEOUT_SECONDS) as response:
            response.read()
    except Exception:
        pass


# -------------------------------------------------------------------------- auth
def _auth(conn, request: Request) -> dict:
    """Resolves X-Auth-Token to a users row, exactly like PHP's auth_user()."""
    token = (request.headers.get("x-auth-token") or "").strip()
    if not token:
        raise Fail("Please log in again.", 401)
    row = conn.execute(text("SELECT * FROM users WHERE api_token = :t"), {"t": token}).mappings().first()
    if row is None:
        raise Fail("Session expired. Please log in again.", 401)
    if row["is_banned"]:
        raise Fail("This account has been suspended.", 403)
    return dict(row)


def _user_json(conn, user: dict) -> dict:
    kyc = conn.execute(
        text("SELECT status FROM kyc_requests WHERE user_id = :u ORDER BY kyc_id DESC LIMIT 1"),
        {"u": user["user_id"]}).scalar()
    return {
        "user_id": int(user["user_id"]),
        "full_name": user["full_name"],
        "username": user["username"],
        "email": user["email"],
        "location": user["location"],
        "role": user["role"],
        "verified": bool(user["is_verified"]),
        "kyc_status": kyc or "none",
    }


def _hash_password(plain: str) -> str:
    return _pwd.hash(plain)


def _verify_password(plain: str, stored: str | None) -> bool:
    if not plain or not stored:
        return False
    # PHP's password_hash() writes $2y$ prefixes; bcrypt accepts $2b$.
    if stored.startswith("$2y$"):
        stored = "$2b$" + stored[4:]
    try:
        return _pwd.verify(plain, stored)
    except ValueError:
        return False


def _listing_json(request: Request, row, me: int) -> dict:
    return {
        "listing_id": int(row["listing_id"]),
        "seller_id": int(row["seller_id"]),
        "title": row["title"],
        "description": row["description"] or "",
        "price": _f(row["price"]),
        "category_id": int(row["category_id"]),
        "category": row["category"],
        "seller": row["seller"],
        "seller_verified": bool(row["seller_verified"]),
        "status": row["status"],
        "rating": _f(row["rating"]),
        "review_count": int(row["review_count"]),
        "image_url": _media_url(request, row["photo"]),
        "is_favorite": bool(row["is_favorite"]),
        "is_owner": int(row["seller_id"]) == me,
        "created_at": _ts(row["created_at"]),
    }


# ------------------------------------------------------------------------- media
def _image_ext(data: bytes) -> str | None:
    if data[:3] == b"\xff\xd8\xff":
        return "jpg"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def _content_type(name: str) -> str:
    lowered = name.lower()
    if lowered.endswith((".m4a", ".aac", ".mp4")):
        return "audio/mp4"
    if lowered.endswith(".png"):
        return "image/png"
    if lowered.endswith(".webp"):
        return "image/webp"
    if lowered.endswith((".jpg", ".jpeg")):
        return "image/jpeg"
    return "application/octet-stream"


def _put_blob(conn, name: str, content_type: str, data: bytes) -> None:
    conn.execute(
        text("INSERT INTO media_blob (name, content_type, data) VALUES (:n, :c, :d) "
             "ON CONFLICT (name) DO UPDATE SET content_type = EXCLUDED.content_type, data = EXCLUDED.data"),
        {"n": name, "c": content_type, "d": data})


def _delete_blob(conn, name: str | None) -> None:
    if name and not name.startswith("http"):
        conn.execute(text("DELETE FROM media_blob WHERE name = :n"), {"n": name})


async def _save_image(conn, files: dict[str, Any], field: str, required: bool,
                      folder: str = "") -> str | None:
    """Stores an uploaded image in media_blob and returns its name (or None)."""
    upload = files.get(field)
    data = await upload.read() if upload is not None else b""
    if not data:
        if required:
            raise Fail("Please choose a photo.")
        return None
    if len(data) > MAX_UPLOAD_BYTES:
        raise Fail("Photo is too large (max 8 MB).")
    ext = _image_ext(data)
    if ext is None:
        raise Fail("That file is not a valid image.")
    name = f"{folder}{secrets.token_hex(12)}.{ext}"
    _put_blob(conn, name, _content_type(name), data)
    return name


@router.get("/uploads/{name:path}", include_in_schema=False)
@php_endpoint
async def serve_upload(request: Request, name: str):
    """Streams a stored photo / voice note. Supports byte ranges for audio."""
    if not name or ".." in name or name.startswith("private/"):
        raise Fail("File not found.", 404)
    data: bytes | None = None
    with engine.connect() as conn:
        row = conn.execute(text("SELECT content_type, data FROM media_blob WHERE name = :n"),
                           {"n": name}).first()
    if row is not None:
        data = bytes(row[1])
        media_type = row[0] or _content_type(name)
    elif LOCAL_UPLOAD_DIR:
        candidate = os.path.join(LOCAL_UPLOAD_DIR, name)
        if os.path.isfile(candidate):
            with open(candidate, "rb") as handle:
                data = handle.read()
        media_type = _content_type(name)
    else:
        media_type = _content_type(name)
    if data is None:
        raise Fail("File not found.", 404)

    # Upload names are random and never overwritten, so browsers, the phone and
    # Vercel's edge may cache the bytes for good. s-maxage keeps photo loading
    # off the cold serverless function, which otherwise takes longer than the
    # mobile client's image timeout.
    headers = {"Accept-Ranges": "bytes",
               "Cache-Control": "public, max-age=31536000, s-maxage=31536000, immutable"}
    range_header = request.headers.get("range")
    if range_header:
        match = re.match(r"bytes=(\d*)-(\d*)", range_header)
        if match and (match.group(1) or match.group(2)):
            total = len(data)
            if match.group(1):
                start = int(match.group(1))
                end = int(match.group(2)) if match.group(2) else total - 1
            else:  # suffix range: last N bytes
                start, end = max(total - int(match.group(2)), 0), total - 1
            end = min(end, total - 1)
            if 0 <= start <= end:
                chunk = data[start:end + 1]
                return Response(content=chunk, status_code=206, media_type=media_type,
                                headers={**headers,
                                         "Content-Range": f"bytes {start}-{end}/{total}",
                                         "Content-Length": str(len(chunk))})
    return Response(content=data, media_type=media_type, headers=headers)


# ====================================================================== accounts
@router.post("/register.php", summary="Register (mobile contract)")
@php_endpoint
async def register_php(request: Request):
    values, _files = await _params(request)
    name = _p(values, "full_name")
    username = _p(values, "username")
    email = _p(values, "email")
    password = values.get("password", "")
    if not name or not username or not email or not password:
        raise Fail("Please fill in all fields.")
    if not EMAIL_RE.match(email) or len(email) > 120:
        raise Fail("Please enter a valid email address.")
    if not USERNAME_RE.match(username):
        raise Fail("Username must be 3-30 characters (letters, numbers, . or _).")
    if len(password) < 6:
        raise Fail("Password must be at least 6 characters.")
    if _p(values, "agree_terms") != "1":
        raise Fail("Please accept the Terms & Community Rules to continue.")

    with engine.begin() as conn:
        taken = conn.execute(text("SELECT 1 FROM users WHERE LOWER(username) = LOWER(:u)"),
                             {"u": username}).first()
        if taken:
            raise Fail("That username is already taken.")
        taken = conn.execute(text("SELECT 1 FROM users WHERE LOWER(email) = LOWER(:e)"),
                             {"e": email}).first()
        if taken:
            raise Fail("That email is already registered.")
        token = secrets.token_hex(32)
        row = conn.execute(
            text("INSERT INTO users (full_name, username, email, password_hash, api_token) "
                 "VALUES (:n, :u, :e, :p, :t) RETURNING *"),
            {"n": name[:100], "u": username, "e": email, "p": _hash_password(password), "t": token}
        ).mappings().first()
        return _ok({"token": token, "user": _user_json(conn, dict(row))}, "Account created successfully!")


@router.post("/login.php", summary="Log in (mobile contract)")
@php_endpoint
async def login_php(request: Request):
    values, _files = await _params(request)
    login = _p(values, "login")
    password = values.get("password", "")
    if not login or not password:
        raise Fail("Enter your email/username and password.")
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT * FROM users WHERE LOWER(email) = LOWER(:a) OR LOWER(username) = LOWER(:b)"),
            {"a": login, "b": login}).mappings().first()
        user = dict(row) if row else None
        if user is None or not _verify_password(password, user["password_hash"]):
            raise Fail("Incorrect email/username or password.", 401)
        if user["is_banned"]:
            raise Fail("This account has been suspended.", 403)
        token = secrets.token_hex(32)
        conn.execute(text("UPDATE users SET api_token = :t WHERE user_id = :id"),
                     {"t": token, "id": user["user_id"]})
        return _ok({"token": token, "user": _user_json(conn, user)}, "Welcome back!")


@router.post("/logout.php", summary="Log out (mobile contract)")
@php_endpoint
async def logout_php(request: Request):
    with engine.begin() as conn:
        user = _auth(conn, request)
        conn.execute(text("UPDATE users SET api_token = NULL WHERE user_id = :id"), {"id": user["user_id"]})
    return _ok(None, "Logged out")


@router.api_route("/profile.php", methods=["GET", "POST"], summary="Profile (mobile contract)")
@php_endpoint
async def profile_php(request: Request):
    values, _files = await _params(request)
    with engine.begin() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        if request.method == "POST":
            name = _p(values, "full_name")
            location = _p(values, "location")
            if not name:
                raise Fail("Name cannot be empty.")
            if len(name) > 100:
                raise Fail("Name is too long.")
            if not location:
                location = me["location"]
            conn.execute(text("UPDATE users SET full_name = :n, location = :l WHERE user_id = :u"),
                         {"n": name, "l": location[:120], "u": uid})

        target = _i(values, "user_id") or uid
        row = conn.execute(text("SELECT * FROM users WHERE user_id = :u"), {"u": target}).mappings().first()
        if row is None:
            raise Fail("User not found.", 404)
        user = dict(row)

        stats = conn.execute(
            text("""SELECT
                (SELECT COUNT(*) FROM listings WHERE seller_id = :a AND is_removed = FALSE) AS listings,
                (SELECT COUNT(*) FROM listings WHERE seller_id = :b AND status = 'sold' AND is_removed = FALSE) AS sold,
                (SELECT COALESCE(ROUND(AVG(rating)::numeric, 1), 0) FROM reviews WHERE seller_id = :c) AS rating,
                (SELECT COUNT(*) FROM reviews WHERE seller_id = :d) AS review_count"""),
            {"a": target, "b": target, "c": target, "d": target}).mappings().first()

        if target == uid:
            data = _user_json(conn, user)
            data["credits"] = _f(user["credits"])
        else:
            data = {"user_id": int(user["user_id"]), "full_name": user["full_name"],
                    "username": user["username"], "location": user["location"],
                    "verified": bool(user["is_verified"])}
        data["listing_count"] = int(stats["listings"])
        data["sold_count"] = int(stats["sold"])
        data["rating"] = _f(stats["rating"])
        data["review_count"] = int(stats["review_count"])
        return _ok(data, "Profile updated." if request.method == "POST" else "OK")


@router.post("/kyc_submit.php", summary="Submit KYC (mobile contract)")
@php_endpoint
async def kyc_submit_php(request: Request):
    values, files = await _params(request)
    with engine.begin() as conn:
        me = _auth(conn, request)
        if me["is_verified"]:
            raise Fail("Your account is already verified.")
        id_type = _p(values, "id_type")
        id_number = _p(values, "id_number")
        if not id_type or not id_number:
            raise Fail("Please enter your ID type and number.")
        if len(id_type) > 60 or len(id_number) > 60:
            raise Fail("ID details are too long.")
        pending = conn.execute(
            text("SELECT 1 FROM kyc_requests WHERE user_id = :u AND status = 'pending'"),
            {"u": me["user_id"]}).first()
        if pending:
            raise Fail("Your verification is already under review.")
        # KYC scans are private: stored under private/ and never served publicly.
        photo = await _save_image(conn, files, "id_photo", True, folder="private/")
        conn.execute(
            text("INSERT INTO kyc_requests (user_id, id_type, id_number, id_photo) "
                 "VALUES (:u, :t, :n, :p)"),
            {"u": me["user_id"], "t": id_type, "n": id_number, "p": photo})
        return _ok({"kyc_status": "pending"}, "Verification submitted. An admin will review it shortly.")


# ====================================================================== listings
@router.get("/categories.php", summary="Categories (mobile contract)")
@php_endpoint
async def categories_php(request: Request):
    with engine.connect() as conn:
        _auth(conn, request)
        rows = conn.execute(text("SELECT category_id, name, icon_key FROM categories ORDER BY category_id")).mappings().all()
    return _ok([{"category_id": int(r["category_id"]), "name": r["name"], "icon_key": r["icon_key"]}
                for r in rows])


@router.get("/listings.php", summary="Browse listings (mobile contract)")
@php_endpoint
async def listings_php(request: Request):
    values, _files = await _params(request)
    with engine.connect() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        where = ["l.is_removed = FALSE", "u.is_banned = FALSE"]
        args: dict[str, Any] = {"me": uid}

        if _p(values, "mine") == "1":
            where.append("l.seller_id = :mine")
            args["mine"] = uid
        else:
            if _p(values, "favorites") == "1":
                where.append("EXISTS (SELECT 1 FROM favorites fv WHERE fv.user_id = :favu "
                             "AND fv.listing_id = l.listing_id)")
                args["favu"] = uid
            elif _p(values, "seller_id"):
                where.append("l.seller_id = :sid")
                args["sid"] = _i(values, "seller_id")
                where.append("l.status <> 'sold'")
            else:
                where.append("l.status <> 'sold'")

        if _i(values, "category_id") > 0:
            where.append("l.category_id = :cat")
            args["cat"] = _i(values, "category_id")

        query = _p(values, "q")
        if query:
            like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where.append(r"(l.title ILIKE :q1 OR l.description ILIKE :q2 OR c.name ILIKE :q3)")
            args["q1"] = args["q2"] = args["q3"] = like

        sql = LISTING_SELECT + " WHERE " + " AND ".join(where) + \
            " ORDER BY l.created_at DESC, l.listing_id DESC LIMIT 100"
        rows = conn.execute(text(sql), args).mappings().all()
    return _ok([_listing_json(request, r, uid) for r in rows])


@router.get("/listing.php", summary="Listing details (mobile contract)")
@php_endpoint
async def listing_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "id")
    if listing_id <= 0:
        raise Fail("Missing listing id.")
    with engine.connect() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        base = conn.execute(text("SELECT seller_id, is_removed FROM listings WHERE listing_id = :id"),
                            {"id": listing_id}).mappings().first()
        if base is None or (base["is_removed"] and int(base["seller_id"]) != uid and me["role"] != "admin"):
            raise Fail("This listing is no longer available.", 404)
        row = conn.execute(text(LISTING_SELECT + " WHERE l.listing_id = :id"),
                           {"me": uid, "id": listing_id}).mappings().first()
        listing = _listing_json(request, row, uid)
        specs = conn.execute(
            text("SELECT spec_name AS name, spec_value AS value FROM listing_specs "
                 "WHERE listing_id = :id ORDER BY spec_id"), {"id": listing_id}).mappings().all()
        listing["specs"] = [{"name": s["name"], "value": s["value"]} for s in specs]
    return _ok(listing)


@router.post("/listing_create.php", summary="Post a listing (mobile contract)")
@php_endpoint
async def listing_create_php(request: Request):
    values, files = await _params(request)
    title = _p(values, "title")
    description = _p(values, "description")
    category_id = _i(values, "category_id")
    price_text = _p(values, "price").replace(",", "")
    if not title:
        raise Fail("Please enter an item title.")
    if len(title) > 120:
        raise Fail("Title is too long (max 120 characters).")
    if category_id <= 0:
        raise Fail("Please select a category.")
    try:
        price = round(float(price_text), 2)
    except ValueError:
        raise Fail("Please enter a valid price.")
    if price < 0:
        raise Fail("Please enter a valid price.")
    if price > 99999999:
        raise Fail("Price is too high.")

    with engine.begin() as conn:
        me = _auth(conn, request)
        if conn.execute(text("SELECT 1 FROM categories WHERE category_id = :c"),
                        {"c": category_id}).first() is None:
            raise Fail("Unknown category.")

        # The flat posting fee is deducted atomically with creating the listing.
        balance = conn.execute(text("SELECT credits FROM users WHERE user_id = :s FOR UPDATE"),
                               {"s": me["user_id"]}).scalar()
        balance = _f(balance)
        if balance < CREDIT_POSTING_FEE:
            raise Fail(f"Not enough wallet credits. Posting an item costs {CREDIT_POSTING_FEE} "
                       f"digital credits (you have {balance:,.2f}). Top up and wait for admin "
                       f"approval, then post again.")
        new_balance = round(balance - CREDIT_POSTING_FEE, 2)
        conn.execute(text("UPDATE users SET credits = :c WHERE user_id = :u"),
                     {"c": new_balance, "u": me["user_id"]})

        photo = await _save_image(conn, files, "photo", False)
        listing_id = conn.execute(
            text("INSERT INTO listings (seller_id, category_id, title, description, price, photo) "
                 "VALUES (:s, :c, :t, :d, :p, :ph) RETURNING listing_id"),
            {"s": me["user_id"], "c": category_id, "t": title, "d": description,
             "p": price, "ph": photo}).scalar()

        conn.execute(text("INSERT INTO credit_ledger (user_id, amount, kind, note) "
                         "VALUES (:u, :a, 'posting_fee', :n)"),
                     {"u": me["user_id"], "a": -CREDIT_POSTING_FEE, "n": f"Posting fee: {title[:120]}"})

        specs_raw = values.get("specs") or "[]"
        try:
            import json
            specs = json.loads(specs_raw)
        except ValueError:
            specs = []
        if isinstance(specs, list):
            for spec in specs[:20]:
                if not isinstance(spec, dict):
                    continue
                spec_name = str(spec.get("name", "")).strip()[:60]
                spec_value = str(spec.get("value", "")).strip()[:120]
                if spec_name and spec_value:
                    conn.execute(text("INSERT INTO listing_specs (listing_id, spec_name, spec_value) "
                                      "VALUES (:l, :n, :v)"),
                                 {"l": listing_id, "n": spec_name, "v": spec_value})

    # Committed above, so the warming fetch can read the stored bytes.
    _warm_upload(photo)

    return _ok({"listing_id": int(listing_id), "credits": new_balance},
               f"Listing posted! {CREDIT_POSTING_FEE} digital credits were deducted from your wallet.")


@router.post("/listing_action.php", summary="Manage a listing (mobile contract)")
@php_endpoint
async def listing_action_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    action = _p(values, "action")
    with engine.begin() as conn:
        me = _auth(conn, request)
        row = conn.execute(text("SELECT * FROM listings WHERE listing_id = :id AND is_removed = FALSE"),
                           {"id": listing_id}).mappings().first()
        if row is None:
            raise Fail("Listing not found.", 404)
        if int(row["seller_id"]) != int(me["user_id"]):
            raise Fail("You can only manage your own listings.", 403)

        if action == "set_status":
            new_status = _p(values, "status")
            if new_status not in ("available", "pending"):
                raise Fail('Use "Mark as sold" to record a sale.')
            if row["status"] == "sold":
                raise Fail("This item is already sold.")
            conn.execute(text("UPDATE listings SET status = :s WHERE listing_id = :id"),
                         {"s": new_status, "id": listing_id})
            return _ok(None, f"Listing marked as {new_status}.")
        if action == "delete":
            conn.execute(text("DELETE FROM listings WHERE listing_id = :id"), {"id": listing_id})
            _delete_blob(conn, row["photo"])
            return _ok(None, "Listing deleted.")
    raise Fail("Unknown action.")


@router.post("/favorite_toggle.php", summary="Toggle favorite (mobile contract)")
@php_endpoint
async def favorite_toggle_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    with engine.begin() as conn:
        me = _auth(conn, request)
        found = conn.execute(text("SELECT 1 FROM listings WHERE listing_id = :id AND is_removed = FALSE"),
                             {"id": listing_id}).first()
        if found is None:
            raise Fail("Listing not found.", 404)
        removed = conn.execute(text("DELETE FROM favorites WHERE user_id = :u AND listing_id = :l"),
                               {"u": me["user_id"], "l": listing_id})
        if removed.rowcount and removed.rowcount > 0:
            return _ok({"is_favorite": False}, "Removed from favorites.")
        conn.execute(text("INSERT INTO favorites (user_id, listing_id) VALUES (:u, :l) "
                          "ON CONFLICT DO NOTHING"),
                     {"u": me["user_id"], "l": listing_id})
        return _ok({"is_favorite": True}, "Added to favorites.")


# ============================================================ comments and chat
@router.api_route("/comments.php", methods=["GET", "POST"], summary="Listing comments (mobile contract)")
@php_endpoint
async def comments_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    if listing_id <= 0:
        raise Fail("Missing listing id.")
    with engine.begin() as conn:
        me = _auth(conn, request)
        found = conn.execute(text("SELECT 1 FROM listings WHERE listing_id = :id AND is_removed = FALSE"),
                             {"id": listing_id}).first()
        if found is None:
            raise Fail("Listing not found.", 404)
        if request.method == "POST":
            body = _p(values, "body")
            if not body:
                raise Fail("Write a comment first.")
            if len(body) > 500:
                raise Fail("Comment is too long (max 500 characters).")
            conn.execute(text("INSERT INTO comments (listing_id, user_id, body) VALUES (:l, :u, :b)"),
                         {"l": listing_id, "u": me["user_id"], "b": body})
        rows = conn.execute(
            text("""SELECT c.comment_id, c.user_id, c.body, c.created_at, u.full_name AS name,
                           u.is_verified AS verified
                    FROM comments c JOIN users u ON u.user_id = c.user_id
                    WHERE c.listing_id = :l ORDER BY c.comment_id ASC LIMIT 200"""),
            {"l": listing_id}).mappings().all()
    data = [{"comment_id": int(r["comment_id"]), "user_id": int(r["user_id"]), "name": r["name"],
             "verified": bool(r["verified"]), "body": r["body"], "created_at": _ts(r["created_at"]),
             "is_mine": int(r["user_id"]) == int(me["user_id"])} for r in rows]
    return _ok(data, "Comment posted." if request.method == "POST" else "OK")


@router.get("/conversations.php", summary="Conversation list (mobile contract)")
@php_endpoint
async def conversations_php(request: Request):
    with engine.connect() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        rows = conn.execute(
            text("""
            WITH m AS (
                SELECT message_id, listing_id, sender_id, receiver_id, body, type, created_at,
                       CASE WHEN sender_id = :a THEN receiver_id ELSE sender_id END AS other_id
                FROM messages WHERE sender_id = :b OR receiver_id = :c
            )
            SELECT DISTINCT ON (m.listing_id, m.other_id)
                   m.listing_id, m.other_id, m.body AS last_message, m.type AS last_type, m.created_at,
                   l.title AS listing_title, u.full_name AS other_name, u.is_verified AS other_verified,
                   (SELECT COUNT(*) FROM messages x WHERE x.listing_id = m.listing_id AND x.sender_id = m.other_id
                      AND x.receiver_id = :d AND x.is_read = FALSE) AS unread
            FROM m
            JOIN listings l ON l.listing_id = m.listing_id
            JOIN users u ON u.user_id = m.other_id
            ORDER BY m.listing_id, m.other_id, m.message_id DESC"""),
            {"a": uid, "b": uid, "c": uid, "d": uid}).mappings().all()

    conversations = []
    for r in rows:
        last_type = r["last_type"]
        preview = "\U0001F4F7 Photo" if last_type == "photo" else (
            "\U0001F3A4 Voice message" if last_type == "voice" else r["last_message"])
        conversations.append({
            "listing_id": int(r["listing_id"]), "other_id": int(r["other_id"]),
            "other_name": r["other_name"], "other_verified": bool(r["other_verified"]),
            "listing_title": r["listing_title"], "last_message": preview,
            "created_at": _ts(r["created_at"]), "unread": int(r["unread"]),
        })
    conversations.sort(key=lambda c: c["created_at"] or "", reverse=True)
    return _ok(conversations)


@router.api_route("/messages.php", methods=["GET", "POST"], summary="Chat messages (mobile contract)")
@php_endpoint
async def messages_php(request: Request):
    """Text, photo and voice messages plus emoji reactions for one listing.

    A conversation stays reachable even after an administrator removes the
    listing (only a missing listing row is a hard 404), otherwise old chats
    would disappear from the inbox.
    """
    values, files = await _params(request)
    listing_id = _i(values, "listing_id")
    other_id = _i(values, "other_id")
    if listing_id <= 0 or other_id <= 0:
        raise Fail("Missing conversation details.")

    with engine.begin() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        if other_id == uid:
            raise Fail("You cannot message yourself.")

        seller_id = conn.execute(text("SELECT seller_id FROM listings WHERE listing_id = :l"),
                                 {"l": listing_id}).scalar()
        if seller_id is None:
            raise Fail("Listing not found.", 404)
        if int(seller_id) not in (uid, other_id):
            raise Fail("Messages must be between a buyer and the seller of this listing.", 403)

        other = conn.execute(text("SELECT is_banned FROM users WHERE user_id = :u"),
                             {"u": other_id}).mappings().first()
        if other is None:
            raise Fail("User not found.", 404)
        banned = bool(other["is_banned"])

        if request.method == "POST":
            action = _p(values, "action")
            if action == "react":
                message_id = _i(values, "message_id")
                emoji = values.get("reaction", "")
                emoji = emoji.strip() if isinstance(emoji, str) else ""
                if emoji not in REACTIONS:
                    raise Fail("Invalid reaction.")
                msg = conn.execute(text("SELECT sender_id, receiver_id FROM messages "
                                        "WHERE message_id = :m AND listing_id = :l"),
                                   {"m": message_id, "l": listing_id}).mappings().first()
                if msg is None:
                    raise Fail("Message not found.", 404)
                if int(msg["sender_id"]) != uid and int(msg["receiver_id"]) != uid:
                    raise Fail("You cannot react to this message.", 403)
                if emoji == "":
                    conn.execute(text("UPDATE messages SET reaction = '', reaction_user_id = NULL "
                                      "WHERE message_id = :m AND reaction_user_id = :u"),
                                 {"m": message_id, "u": uid})
                else:
                    conn.execute(text("UPDATE messages SET reaction = :r, reaction_user_id = :u "
                                      "WHERE message_id = :m"),
                                 {"r": emoji, "u": uid, "m": message_id})
            else:
                if banned:
                    raise Fail("You cannot message this user.")
                msg_type = _p(values, "type") or "text"
                if msg_type not in ("text", "photo", "voice"):
                    raise Fail("Invalid message type.")
                if msg_type == "text":
                    body = _p(values, "body")
                    if not body:
                        raise Fail("Type a message first.")
                    if len(body) > MAX_MESSAGE_LENGTH:
                        raise Fail("Message is too long.")
                    conn.execute(text("INSERT INTO messages (listing_id, sender_id, receiver_id, body, type) "
                                      "VALUES (:l, :s, :r, :b, 'text')"),
                                 {"l": listing_id, "s": uid, "r": other_id, "b": body})
                else:
                    upload = files.get("file")
                    data = await upload.read() if upload is not None else b""
                    if not data:
                        raise Fail("Nothing to send.")
                    if len(data) > MAX_UPLOAD_BYTES:
                        raise Fail("File is too large (max 8 MB). Try a smaller file.")
                    if msg_type == "photo":
                        ext = _image_ext(data)
                        if ext is None:
                            raise Fail("That file is not a valid image.")
                        name = f"{secrets.token_hex(12)}.{ext}"
                        _put_blob(conn, name, _content_type(name), data)
                        conn.execute(text("INSERT INTO messages (listing_id, sender_id, receiver_id, "
                                           "type, photo) VALUES (:l, :s, :r, 'photo', :p)"),
                                     {"l": listing_id, "s": uid, "r": other_id, "p": name})
                    else:
                        if len(data) < 500:
                            raise Fail("Recording is too short.")
                        duration = min(120, max(1, _i(values, "duration", 1)))
                        name = f"voice/{secrets.token_hex(12)}.m4a"
                        _put_blob(conn, name, "audio/mp4", data)
                        conn.execute(text("INSERT INTO messages (listing_id, sender_id, receiver_id, "
                                           "type, photo, duration) VALUES (:l, :s, :r, 'voice', :p, :d)"),
                                     {"l": listing_id, "s": uid, "r": other_id, "p": name, "d": duration})

        conn.execute(text("UPDATE messages SET is_read = TRUE WHERE listing_id = :l AND sender_id = :o "
                          "AND receiver_id = :m AND is_read = FALSE"),
                     {"l": listing_id, "o": other_id, "m": uid})
        rows = conn.execute(
            text("""SELECT message_id, sender_id, body, type, photo, duration, reaction,
                           reaction_user_id, created_at
                    FROM messages
                    WHERE listing_id = :l AND ((sender_id = :a AND receiver_id = :b)
                        OR (sender_id = :c AND receiver_id = :d))
                    ORDER BY message_id ASC LIMIT 500"""),
            {"l": listing_id, "a": uid, "b": other_id, "c": other_id, "d": uid}).mappings().all()

    data = [{"message_id": int(r["message_id"]), "body": r["body"], "type": r["type"],
             "photo_url": _media_url(request, r["photo"]), "duration": int(r["duration"] or 0),
             "reaction": r["reaction"] or "",
             "reaction_mine": r["reaction_user_id"] is not None and int(r["reaction_user_id"]) == uid,
             "created_at": _ts(r["created_at"]), "is_mine": int(r["sender_id"]) == uid} for r in rows]
    return _ok(data)


# ======================================================================== wallet
def _wallet_snapshot(conn, uid: int) -> dict:
    balance = _f(conn.execute(text("SELECT credits FROM users WHERE user_id = :u"), {"u": uid}).scalar())
    ledger = conn.execute(text("SELECT amount, kind, note, created_at FROM credit_ledger "
                               "WHERE user_id = :u ORDER BY ledger_id DESC LIMIT 100"),
                          {"u": uid}).mappings().all()
    requests = conn.execute(text("SELECT amount, payment_method, payment_reference, status, created_at "
                                 "FROM topup_requests WHERE user_id = :u ORDER BY request_id DESC LIMIT 10"),
                            {"u": uid}).mappings().all()
    return {
        "balance": balance,
        "posting_fee": CREDIT_POSTING_FEE,
        "ledger": [{"amount": _f(r["amount"]), "kind": r["kind"], "note": r["note"],
                    "created_at": _ts(r["created_at"])} for r in ledger],
        "topup_requests": [{"amount": _f(r["amount"]), "payment_method": r["payment_method"],
                            "payment_reference": r["payment_reference"], "status": r["status"],
                            "created_at": _ts(r["created_at"])} for r in requests],
        "payment_instructions": TOPUP_PAYMENT_INSTRUCTIONS,
        "minimum_topup": TOPUP_MIN,
        "maximum_topup": TOPUP_MAX,
    }


@router.api_route("/wallet.php", methods=["GET", "POST"], summary="Wallet (mobile contract)")
@php_endpoint
async def wallet_php(request: Request):
    values, _files = await _params(request)
    with engine.begin() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])
        if request.method == "POST":
            amount_text = _p(values, "amount")
            method = _p(values, "payment_method")
            reference = _p(values, "payment_reference")
            if not re.match(r"^\d+(\.\d{1,2})?$", amount_text):
                raise Fail("Enter a valid amount.")
            amount = round(float(amount_text), 2)
            if amount < TOPUP_MIN:
                raise Fail(f"Minimum top-up is \u20B1{TOPUP_MIN:,.2f}.")
            if amount > TOPUP_MAX:
                raise Fail(f"Maximum top-up is \u20B1{TOPUP_MAX:,.2f}.")
            if method not in TOPUP_METHODS:
                raise Fail("Choose GCash, PayMaya, or bank transfer.")
            if not 3 <= len(reference) <= 120:
                raise Fail("Enter a payment reference between 3 and 120 characters.")
            conn.execute(text("SELECT user_id FROM users WHERE user_id = :u FOR UPDATE"), {"u": uid})
            pending = conn.execute(text("SELECT 1 FROM topup_requests WHERE user_id = :u "
                                        "AND status = 'pending' LIMIT 1"), {"u": uid}).first()
            if pending:
                raise Fail("You already have a top-up request awaiting review.")
            conn.execute(text("INSERT INTO topup_requests (user_id, amount, payment_method, "
                              "payment_reference) VALUES (:u, :a, :m, :r)"),
                         {"u": uid, "a": amount, "m": method, "r": reference})
            return _ok(_wallet_snapshot(conn, uid),
                       "Request submitted. Credits will be added after an administrator verifies "
                       "your payment.")
        return _ok(_wallet_snapshot(conn, uid))


# ================================================================== trust & sales
@router.post("/review_add.php", summary="Rate a seller (mobile contract)")
@php_endpoint
async def review_add_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    rating = _i(values, "rating")
    comment = _p(values, "comment")
    if rating < 1 or rating > 5:
        raise Fail("Please select a star rating.")
    if len(comment) > 500:
        raise Fail("Comment is too long (max 500 characters).")
    with engine.begin() as conn:
        me = _auth(conn, request)
        seller_id = conn.execute(text("SELECT seller_id FROM listings WHERE listing_id = :l "
                                      "AND is_removed = FALSE"), {"l": listing_id}).scalar()
        if seller_id is None:
            raise Fail("Listing not found.", 404)
        if int(seller_id) == int(me["user_id"]):
            raise Fail("You cannot rate yourself.")
        conn.execute(
            text("INSERT INTO reviews (listing_id, reviewer_id, seller_id, rating, comment) "
                 "VALUES (:l, :r, :s, :rt, :c) "
                 "ON CONFLICT (listing_id, reviewer_id) DO UPDATE SET rating = EXCLUDED.rating, "
                 "comment = EXCLUDED.comment, created_at = NOW()"),
            {"l": listing_id, "r": me["user_id"], "s": int(seller_id), "rt": rating, "c": comment})
    return _ok(None, "Thanks for your review!")


@router.post("/report_add.php", summary="Report a listing (mobile contract)")
@php_endpoint
async def report_add_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    reason = _p(values, "reason")
    details = _p(values, "details")
    if reason not in REPORT_REASONS:
        raise Fail("Please choose a reason.")
    if len(details) > 500:
        raise Fail("Details are too long (max 500 characters).")
    with engine.begin() as conn:
        me = _auth(conn, request)
        seller_id = conn.execute(text("SELECT seller_id FROM listings WHERE listing_id = :l "
                                      "AND is_removed = FALSE"), {"l": listing_id}).scalar()
        if seller_id is None:
            raise Fail("Listing not found.", 404)
        if int(seller_id) == int(me["user_id"]):
            raise Fail("You cannot report your own listing.")
        inserted = conn.execute(
            text("INSERT INTO reports (listing_id, reporter_id, reason, details) "
                 "VALUES (:l, :r, :re, :d) ON CONFLICT (listing_id, reporter_id) DO NOTHING "
                 "RETURNING report_id"),
            {"l": listing_id, "r": me["user_id"], "re": reason, "d": details}).first()
        if inserted is None:
            raise Fail("You already reported this listing. Our admins will review it.")
    return _ok(None, "Report submitted. Thank you for keeping BaryoBenta safe.")


@router.get("/listing_buyers.php", summary="Buyers who messaged me (mobile contract)")
@php_endpoint
async def listing_buyers_php(request: Request):
    values, _files = await _params(request)
    listing_id = _i(values, "listing_id")
    with engine.connect() as conn:
        me = _auth(conn, request)
        seller_id = conn.execute(text("SELECT seller_id FROM listings WHERE listing_id = :l"),
                                 {"l": listing_id}).scalar()
        if seller_id is None or int(seller_id) != int(me["user_id"]):
            raise Fail("Only the seller can do this.", 403)
        rows = conn.execute(
            text("""SELECT DISTINCT u.user_id, u.full_name AS name
                    FROM messages m JOIN users u ON u.user_id = m.sender_id
                    WHERE m.listing_id = :l AND m.receiver_id = :me AND u.is_banned = FALSE
                    ORDER BY u.full_name"""),
            {"l": listing_id, "me": me["user_id"]}).mappings().all()
    return _ok([{"user_id": int(r["user_id"]), "name": r["name"]} for r in rows])


@router.api_route("/transactions.php", methods=["GET", "POST"], summary="Sales (mobile contract)")
@php_endpoint
async def transactions_php(request: Request):
    values, _files = await _params(request)
    with engine.begin() as conn:
        me = _auth(conn, request)
        uid = int(me["user_id"])

        if request.method == "POST":
            listing_id = _i(values, "listing_id")
            buyer_id = _i(values, "buyer_id")
            row = conn.execute(text("SELECT * FROM listings WHERE listing_id = :l "
                                    "AND is_removed = FALSE FOR UPDATE"),
                               {"l": listing_id}).mappings().first()
            if row is None:
                raise Fail("Listing not found.", 404)
            if int(row["seller_id"]) != uid:
                raise Fail("Only the seller can record a sale.", 403)
            if row["status"] == "sold":
                raise Fail("This item is already sold.")
            if buyer_id <= 0 or buyer_id == uid:
                raise Fail("Choose the buyer.")
            contacted = conn.execute(text("SELECT 1 FROM messages WHERE listing_id = :l "
                                          "AND sender_id = :b AND receiver_id = :s LIMIT 1"),
                                     {"l": listing_id, "b": buyer_id, "s": uid}).first()
            if contacted is None:
                raise Fail("That user has not contacted you about this item.")
            conn.execute(text("INSERT INTO transactions (listing_id, buyer_id, amount) "
                              "VALUES (:l, :b, :a) ON CONFLICT (listing_id) DO NOTHING"),
                         {"l": listing_id, "b": buyer_id, "a": _f(row["price"])})
            conn.execute(text("UPDATE listings SET status = 'sold' WHERE listing_id = :l"),
                         {"l": listing_id})
            return _ok(None, "Sale recorded. Marked as sold.")

        rows = conn.execute(
            text("""SELECT t.transaction_id, t.listing_id, t.amount, t.created_at, l.title,
                           l.seller_id, t.buyer_id, s.full_name AS seller_name, b.full_name AS buyer_name
                    FROM transactions t
                    JOIN listings l ON l.listing_id = t.listing_id
                    JOIN users s ON s.user_id = l.seller_id
                    JOIN users b ON b.user_id = t.buyer_id
                    WHERE l.seller_id = :a OR t.buyer_id = :b
                    ORDER BY t.created_at DESC"""),
            {"a": uid, "b": uid}).mappings().all()

    data = []
    for r in rows:
        sold = int(r["seller_id"]) == uid
        data.append({"transaction_id": int(r["transaction_id"]), "listing_id": int(r["listing_id"]),
                     "title": r["title"], "amount": _f(r["amount"]), "created_at": _ts(r["created_at"]),
                     "role": "sold" if sold else "bought",
                     "other_name": r["buyer_name"] if sold else r["seller_name"]})
    return _ok(data)

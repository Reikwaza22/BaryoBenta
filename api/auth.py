"""Authentication helpers: bcrypt password hashing and JWT session tokens."""
import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from passlib.context import CryptContext
from sqlalchemy.orm import Session

import models
from database import get_db

# In production this comes from the environment (see .env.example).
SECRET_KEY = os.getenv("SECRET_KEY", "change-me-in-production")
ALGORITHM = "HS256"
TOKEN_TTL_HOURS = 24

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
bearer_scheme = HTTPBearer(auto_error=False)


def hash_password(plain_password: str) -> str:
    """Return a bcrypt hash of the given plain-text password."""
    return pwd_context.hash(plain_password)


def verify_password(plain_password: str, password_hash: str) -> bool:
    """Check a plain-text password against a stored bcrypt hash."""
    try:
        return pwd_context.verify(plain_password, password_hash)
    except ValueError:
        return False


def create_access_token(user_id: int, role: str) -> str:
    """Create a signed JWT that identifies the logged-in member."""
    payload = {
        "sub": str(user_id),
        "role": role,
        "exp": datetime.now(timezone.utc) + timedelta(hours=TOKEN_TTL_HOURS),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> models.User:
    """FastAPI dependency: resolve the Bearer token to a real account."""
    unauthorized = HTTPException(
        status.HTTP_401_UNAUTHORIZED,
        detail="Not authenticated. Send 'Authorization: Bearer <token>' from POST /auth/login.",
    )
    if credentials is None:
        raise unauthorized
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise unauthorized
    user = db.query(models.User).filter(models.User.user_id == user_id).first()
    if user is None:
        raise unauthorized
    return user


def require_admin(current_user: models.User = Depends(get_current_user)) -> models.User:
    """FastAPI dependency: only administrators may proceed."""
    if current_user.role != "Admin":
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Administrator role required.")
    return current_user

from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
from pydantic import BaseModel, EmailStr, Field, field_validator


# ---------- Enums (mirror the DB enums) ----------
class UserRole(str, Enum):
    Buyer = "Buyer"
    Seller = "Seller"
    Admin = "Admin"


class ListingStatus(str, Enum):
    Available = "Available"
    Sold = "Sold"
    Removed = "Removed"


class PaymentMethod(str, Enum):
    Cash = "Cash"
    GCash = "GCash"
    Maya = "Maya"


class TransactionStatus(str, Enum):
    Pending = "Pending"
    Completed = "Completed"
    Cancelled = "Cancelled"


# ---------- USER ----------
class UserCreate(BaseModel):
    full_name: str = Field(..., min_length=2, max_length=120)
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=72)
    phone_number: Optional[str] = Field(None, max_length=20)
    role: UserRole = UserRole.Buyer
    campus_location: Optional[str] = Field(None, max_length=120)

    @field_validator("full_name")
    @classmethod
    def strip_full_name(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("full_name cannot be blank")
        return v


class UserOut(BaseModel):
    user_id: int
    full_name: str
    email: EmailStr
    phone_number: Optional[str]
    role: UserRole
    campus_location: Optional[str]
    created_at: datetime

    class Config:
        from_attributes = True


# ---------- CATEGORY ----------
class CategoryCreate(BaseModel):
    category_name: str = Field(..., min_length=2, max_length=80)


class CategoryOut(BaseModel):
    category_id: int
    category_name: str

    class Config:
        from_attributes = True


# ---------- LISTING ----------
class ListingCreate(BaseModel):
    seller_id: int = Field(..., gt=0)
    category_id: int = Field(..., gt=0)
    title: str = Field(..., min_length=3, max_length=150)
    description: Optional[str] = Field(None, max_length=5000)
    price: Decimal = Field(..., gt=0, le=Decimal("999999.99"))
    item_condition: str = Field(..., min_length=2, max_length=30)


class ListingOut(BaseModel):
    listing_id: int
    seller_id: int
    category_id: int
    title: str
    description: Optional[str]
    price: Decimal
    item_condition: str
    status: ListingStatus
    created_at: datetime

    class Config:
        from_attributes = True


# ---------- TRANSACTION ----------
class TransactionCreate(BaseModel):
    listing_id: int = Field(..., gt=0)
    buyer_id: int = Field(..., gt=0)
    amount: Decimal = Field(..., gt=0)
    payment_method: PaymentMethod


class TransactionOut(BaseModel):
    transaction_id: int
    listing_id: int
    buyer_id: int
    transaction_date: datetime
    amount: Decimal
    payment_method: PaymentMethod
    status: TransactionStatus

    class Config:
        from_attributes = True


# ---------- FAVORITE ----------
class FavoriteCreate(BaseModel):
    user_id: int = Field(..., gt=0)
    listing_id: int = Field(..., gt=0)


class FavoriteOut(BaseModel):
    favorite_id: int
    user_id: int
    listing_id: int
    created_at: datetime

    class Config:
        from_attributes = True


# ---------- REVIEW ----------
class ReviewCreate(BaseModel):
    transaction_id: int = Field(..., gt=0)
    reviewer_id: int = Field(..., gt=0)
    reviewee_id: int = Field(..., gt=0)
    rating: int = Field(..., ge=1, le=5)
    comment: Optional[str] = Field(None, max_length=1000)


class ReviewOut(BaseModel):
    review_id: int
    transaction_id: int
    reviewer_id: int
    reviewee_id: int
    rating: int
    comment: Optional[str]
    created_at: datetime

    class Config:
        from_attributes = True


# ---------- Standard error envelope ----------
class ErrorResponse(BaseModel):
    error: str
    detail: str
    status_code: int

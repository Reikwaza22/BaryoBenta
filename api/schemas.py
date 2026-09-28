from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
from pydantic import BaseModel, EmailStr, Field, field_validator


class UserRole(str, Enum):
    Buyer = "Buyer"; Seller = "Seller"; Admin = "Admin"


class ListingStatus(str, Enum):
    Available = "Available"; Sold = "Sold"; Removed = "Removed"


class TransactionStatus(str, Enum):
    Pending = "Pending"; Completed = "Completed"; Cancelled = "Cancelled"


class UserCreate(BaseModel):
    first_name: str = Field(..., min_length=1, max_length=80)
    last_name:  str = Field(..., min_length=1, max_length=80)
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=72)
    contact_number: Optional[str] = Field(None, max_length=20)
    role: str = "Buyer"

    @field_validator("first_name", "last_name")
    @classmethod
    def strip_name(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("name cannot be blank")
        return v


class UserOut(BaseModel):
    user_id: int
    first_name: str
    last_name: str
    email: EmailStr
    contact_number: Optional[str]
    role: str
    verified_status: Optional[bool] = None
    created_at: datetime

    class Config:
        from_attributes = True


class CategoryCreate(BaseModel):
    category_name: str = Field(..., min_length=2, max_length=80)


class CategoryOut(BaseModel):
    category_id: int
    category_name: str

    class Config:
        from_attributes = True


class ListingCreate(BaseModel):
    seller_id: int = Field(..., gt=0)
    category_id: int = Field(..., gt=0)
    title: str = Field(..., min_length=3, max_length=150)
    description: Optional[str] = Field(None, max_length=5000)
    price: Decimal = Field(..., ge=0, le=Decimal("999999.99"))


class ListingOut(BaseModel):
    listing_id: int
    seller_id: int
    category_id: int
    title: str
    description: Optional[str]
    price: Decimal
    status: ListingStatus
    created_at: datetime

    class Config:
        from_attributes = True


class TransactionCreate(BaseModel):
    listing_id: int = Field(..., gt=0)
    buyer_id: int = Field(..., gt=0)
    meetup_location: Optional[str] = Field(None, max_length=200)
    meetup_datetime: Optional[datetime] = None


class TransactionOut(BaseModel):
    transaction_id: int
    listing_id: int
    buyer_id: int
    meetup_location: Optional[str]
    meetup_datetime: Optional[datetime]
    status: TransactionStatus
    created_at: datetime

    class Config:
        from_attributes = True


class FavoriteCreate(BaseModel):
    user_id: int = Field(..., gt=0)
    listing_id: int = Field(..., gt=0)


class FavoriteOut(BaseModel):
    user_id: int
    listing_id: int
    created_at: datetime

    class Config:
        from_attributes = True


class RatingCreate(BaseModel):
    transaction_id: int = Field(..., gt=0)
    rater_id: int = Field(..., gt=0)
    ratee_id: int = Field(..., gt=0)
    score: int = Field(..., ge=1, le=5)
    comment: Optional[str] = Field(None, max_length=1000)


class RatingOut(BaseModel):
    rating_id: int
    transaction_id: int
    rater_id: int
    ratee_id: int
    score: int
    comment: Optional[str]
    created_at: datetime

    class Config:
        from_attributes = True


class ErrorResponse(BaseModel):
    error: str
    detail: str
    status_code: int

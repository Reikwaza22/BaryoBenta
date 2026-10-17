from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
from pydantic import BaseModel, EmailStr, Field, field_validator, model_validator


class UserRole(str, Enum):
    Buyer = "Buyer"; Seller = "Seller"; Admin = "Admin"


class RegistrableRole(str, Enum):
    """Roles a visitor may choose at registration. Administrator accounts are
    created by the team directly in the database, never through the API."""
    Buyer = "Buyer"; Seller = "Seller"


class ListingStatus(str, Enum):
    Available = "Available"; Sold = "Sold"; Removed = "Removed"


class TransactionStatus(str, Enum):
    Pending = "Pending"; Completed = "Completed"; Cancelled = "Cancelled"


class ReportStatus(str, Enum):
    Open = "Open"; Resolved = "Resolved"; Dismissed = "Dismissed"


class BoostPaymentStatus(str, Enum):
    Pending = "Pending"; Confirmed = "Confirmed"; Rejected = "Rejected"


# ---------------------------------------------------------------- users/auth
class UserCreate(BaseModel):
    first_name: str = Field(..., min_length=1, max_length=80)
    last_name:  str = Field(..., min_length=1, max_length=80)
    email: EmailStr = Field(..., max_length=150)
    password: str = Field(..., min_length=8, max_length=72)
    contact_number: Optional[str] = Field(None, max_length=20)
    role: RegistrableRole = RegistrableRole.Buyer

    @field_validator("first_name", "last_name")
    @classmethod
    def strip_name(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("name cannot be blank")
        return v


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=1, max_length=72)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: int
    role: str


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


# ------------------------------------------------------------------ category
class CategoryCreate(BaseModel):
    category_name: str = Field(..., min_length=2, max_length=80)


class CategoryOut(BaseModel):
    category_id: int
    category_name: str

    class Config:
        from_attributes = True


# ------------------------------------------------------------------- listing
class ListingCreate(BaseModel):
    category_id: int = Field(..., gt=0)
    title: str = Field(..., min_length=3, max_length=150)
    description: Optional[str] = Field(None, max_length=5000)
    price: Decimal = Field(..., ge=0, le=Decimal("999999.99"),
                           description="Price in PHP. 0 is allowed for free items.")


class ListingUpdate(BaseModel):
    title: Optional[str] = Field(None, min_length=3, max_length=150)
    description: Optional[str] = Field(None, max_length=5000)
    price: Optional[Decimal] = Field(None, ge=0, le=Decimal("999999.99"))
    category_id: Optional[int] = Field(None, gt=0)


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


class ListingImageCreate(BaseModel):
    image_url: str = Field(..., min_length=1, max_length=255)
    display_order: int = Field(0, ge=0)


class ListingImageOut(BaseModel):
    image_id: int
    listing_id: int
    image_url: str
    display_order: int

    class Config:
        from_attributes = True


# --------------------------------------------------------------- transaction
class TransactionCreate(BaseModel):
    listing_id: int = Field(..., gt=0)
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


# ------------------------------------------------------------------ favorite
class FavoriteCreate(BaseModel):
    listing_id: int = Field(..., gt=0)


class FavoriteOut(BaseModel):
    user_id: int
    listing_id: int
    created_at: datetime

    class Config:
        from_attributes = True


# ------------------------------------------------------------------- message
class MessageCreate(BaseModel):
    receiver_id: int = Field(..., gt=0)
    listing_id: Optional[int] = Field(None, gt=0)
    message_text: str = Field(..., min_length=1, max_length=2000)


class MessageOut(BaseModel):
    message_id: int
    sender_id: int
    receiver_id: int
    listing_id: Optional[int]
    message_text: str
    sent_at: datetime

    class Config:
        from_attributes = True


# -------------------------------------------------------------------- rating
class RatingCreate(BaseModel):
    transaction_id: int = Field(..., gt=0)
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


# --------------------------------------------------------------------- boost
class BoostCreate(BaseModel):
    listing_id: int = Field(..., gt=0)
    amount: Decimal = Field(..., gt=0, le=Decimal("999999.99"),
                            description="Boost fee in PHP, settled offline and confirmed by an administrator.")
    start_date: Optional[datetime] = None
    end_date: Optional[datetime] = None


class BoostOut(BaseModel):
    boost_id: int
    listing_id: int
    amount: Decimal
    start_date: Optional[datetime]
    end_date: Optional[datetime]
    payment_status: BoostPaymentStatus

    class Config:
        from_attributes = True


class BoostReview(BaseModel):
    payment_status: BoostPaymentStatus


# -------------------------------------------------------------------- report
class ReportCreate(BaseModel):
    listing_id: Optional[int] = Field(None, gt=0)
    reported_user_id: Optional[int] = Field(None, gt=0)
    reason: str = Field(..., min_length=3, max_length=255)

    @model_validator(mode="after")
    def need_a_target(self):
        if self.listing_id is None and self.reported_user_id is None:
            raise ValueError("A report must target a listing, a user, or both.")
        return self


class ReportOut(BaseModel):
    report_id: int
    reporter_id: int
    listing_id: Optional[int]
    reported_user_id: Optional[int]
    reason: str
    status: ReportStatus
    created_at: datetime

    class Config:
        from_attributes = True


class ReportReview(BaseModel):
    status: ReportStatus


class ErrorResponse(BaseModel):
    error: str
    detail: str
    status_code: int

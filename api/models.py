import enum
from sqlalchemy import (
    Column, Integer, String, Text, Numeric, TIMESTAMP, ForeignKey,
    Enum, SmallInteger, UniqueConstraint, CheckConstraint, func
)
from sqlalchemy.orm import relationship
from database import Base


class UserRole(str, enum.Enum):
    Buyer = "Buyer"
    Seller = "Seller"
    Admin = "Admin"


class ListingStatus(str, enum.Enum):
    Available = "Available"
    Sold = "Sold"
    Removed = "Removed"


class PaymentMethod(str, enum.Enum):
    Cash = "Cash"
    GCash = "GCash"
    Maya = "Maya"


class TransactionStatus(str, enum.Enum):
    Pending = "Pending"
    Completed = "Completed"
    Cancelled = "Cancelled"


class User(Base):
    __tablename__ = "app_user"

    user_id = Column(Integer, primary_key=True, index=True)
    full_name = Column(String(120), nullable=False)
    email = Column(String(150), nullable=False, unique=True, index=True)
    password_hash = Column(String(255), nullable=False)
    phone_number = Column(String(20))
    role = Column(Enum(UserRole, name="user_role"), nullable=False, default=UserRole.Buyer)
    campus_location = Column(String(120))
    created_at = Column(TIMESTAMP, server_default=func.now())

    listings = relationship("Listing", back_populates="seller")


class Category(Base):
    __tablename__ = "category"

    category_id = Column(Integer, primary_key=True, index=True)
    category_name = Column(String(80), nullable=False, unique=True)


class Listing(Base):
    __tablename__ = "listing"

    listing_id = Column(Integer, primary_key=True, index=True)
    seller_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    category_id = Column(Integer, ForeignKey("category.category_id"), nullable=False)
    title = Column(String(150), nullable=False)
    description = Column(Text)
    price = Column(Numeric(10, 2), nullable=False)
    item_condition = Column(String(30), nullable=False)
    status = Column(Enum(ListingStatus, name="listing_status"), nullable=False, default=ListingStatus.Available)
    created_at = Column(TIMESTAMP, server_default=func.now())

    __table_args__ = (CheckConstraint("price >= 0", name="chk_listing_price_positive"),)

    seller = relationship("User", back_populates="listings")


class ListingPhoto(Base):
    __tablename__ = "listing_photo"

    photo_id = Column(Integer, primary_key=True, index=True)
    listing_id = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"), nullable=False)
    photo_url = Column(String(255), nullable=False)


class Transaction(Base):
    __tablename__ = "transaction"

    transaction_id = Column(Integer, primary_key=True, index=True)
    listing_id = Column(Integer, ForeignKey("listing.listing_id"), nullable=False)
    buyer_id = Column(Integer, ForeignKey("app_user.user_id"), nullable=False)
    transaction_date = Column(TIMESTAMP, server_default=func.now())
    amount = Column(Numeric(10, 2), nullable=False)
    payment_method = Column(Enum(PaymentMethod, name="payment_method_type"), nullable=False)
    status = Column(Enum(TransactionStatus, name="transaction_status"), nullable=False, default=TransactionStatus.Pending)


class Favorite(Base):
    __tablename__ = "favorite"

    favorite_id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    listing_id = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"), nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())

    __table_args__ = (UniqueConstraint("user_id", "listing_id", name="uq_favorite_user_listing"),)


class Message(Base):
    __tablename__ = "message"

    message_id = Column(Integer, primary_key=True, index=True)
    sender_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    receiver_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    listing_id = Column(Integer, ForeignKey("listing.listing_id", ondelete="SET NULL"), nullable=True)
    message_text = Column(Text, nullable=False)
    sent_at = Column(TIMESTAMP, server_default=func.now())


class Review(Base):
    __tablename__ = "review"

    review_id = Column(Integer, primary_key=True, index=True)
    transaction_id = Column(Integer, ForeignKey("transaction.transaction_id", ondelete="CASCADE"), nullable=False, unique=True)
    reviewer_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    reviewee_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    rating = Column(SmallInteger, nullable=False)
    comment = Column(Text)
    created_at = Column(TIMESTAMP, server_default=func.now())

    __table_args__ = (CheckConstraint("rating BETWEEN 1 AND 5", name="chk_review_rating_range"),)

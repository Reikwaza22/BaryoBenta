import enum
from sqlalchemy import (
    Column, Integer, String, Text, Numeric, TIMESTAMP, ForeignKey,
    SmallInteger, PrimaryKeyConstraint, Boolean, func
)
from sqlalchemy.orm import relationship
from database import Base


class UserRole(str, enum.Enum):
    Buyer = "Buyer"; Seller = "Seller"; Admin = "Admin"


class ListingStatus(str, enum.Enum):
    Available = "Available"; Sold = "Sold"; Removed = "Removed"


class TransactionStatus(str, enum.Enum):
    Pending = "Pending"; Completed = "Completed"; Cancelled = "Cancelled"


class User(Base):
    __tablename__ = "app_user"
    user_id         = Column(Integer, primary_key=True, index=True)
    first_name      = Column(String(80), nullable=False)
    last_name       = Column(String(80), nullable=False)
    email           = Column(String(150), nullable=False, unique=True, index=True)
    password_hash   = Column(String(255), nullable=False)
    contact_number  = Column(String(20))
    role            = Column(String(20), nullable=False, default="buyer")
    verified_status = Column(Boolean, nullable=False, default=False)
    created_at      = Column(TIMESTAMP, server_default=func.now())


class Category(Base):
    __tablename__ = "category"
    category_id   = Column(Integer, primary_key=True, index=True)
    category_name = Column(String(80), nullable=False, unique=True)


class Listing(Base):
    __tablename__ = "listing"
    listing_id  = Column(Integer, primary_key=True, index=True)
    seller_id   = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    category_id = Column(Integer, ForeignKey("category.category_id"), nullable=False)
    title       = Column(String(150), nullable=False)
    description = Column(Text)
    price       = Column(Numeric(10, 2), nullable=False)
    status      = Column(String(20), nullable=False, default="Available")
    created_at  = Column(TIMESTAMP, server_default=func.now())


class ListingImage(Base):
    __tablename__ = "listing_image"
    image_id      = Column(Integer, primary_key=True, index=True)
    listing_id    = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"), nullable=False)
    image_url     = Column(String(255), nullable=False)
    display_order = Column(Integer, nullable=False, default=0)


class Transaction(Base):
    __tablename__ = "transaction"
    transaction_id  = Column(Integer, primary_key=True, index=True)
    listing_id      = Column(Integer, ForeignKey("listing.listing_id"), nullable=False)
    buyer_id        = Column(Integer, ForeignKey("app_user.user_id"), nullable=False)
    meetup_location = Column(String(200))
    meetup_datetime = Column(TIMESTAMP)
    status          = Column(String(20), nullable=False, default="Pending")
    created_at      = Column(TIMESTAMP, server_default=func.now())


class Favorite(Base):
    __tablename__ = "favorite"
    user_id    = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    listing_id = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"), nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())
    __table_args__ = (PrimaryKeyConstraint("user_id", "listing_id", name="favorite_pkey"),)


class Message(Base):
    __tablename__ = "message"
    message_id   = Column(Integer, primary_key=True, index=True)
    sender_id    = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    receiver_id  = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    listing_id   = Column(Integer, ForeignKey("listing.listing_id", ondelete="SET NULL"))
    message_text = Column(Text, nullable=False)
    sent_at      = Column(TIMESTAMP, server_default=func.now())


class Rating(Base):
    __tablename__ = "rating"
    rating_id      = Column(Integer, primary_key=True, index=True)
    transaction_id = Column(Integer, ForeignKey("transaction.transaction_id", ondelete="CASCADE"), nullable=False)
    rater_id       = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    ratee_id       = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    score          = Column(SmallInteger, nullable=False)
    comment        = Column(Text)
    created_at     = Column(TIMESTAMP, server_default=func.now())


class Boost(Base):
    __tablename__ = "boost"
    boost_id       = Column(Integer, primary_key=True, index=True)
    listing_id     = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"), nullable=False)
    amount         = Column(Numeric(10, 2), nullable=False)
    start_date     = Column(TIMESTAMP)
    end_date       = Column(TIMESTAMP)
    payment_status = Column(String(20), nullable=False, default="Pending")


class Report(Base):
    __tablename__ = "report"
    report_id   = Column(Integer, primary_key=True, index=True)
    reporter_id = Column(Integer, ForeignKey("app_user.user_id", ondelete="CASCADE"), nullable=False)
    listing_id  = Column(Integer, ForeignKey("listing.listing_id", ondelete="CASCADE"))
    reason      = Column(String(255), nullable=False)
    status      = Column(String(20), nullable=False, default="Open")
    created_at  = Column(TIMESTAMP, server_default=func.now())

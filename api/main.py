import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy import or_, and_, select, text

import csv
from io import StringIO

import models
import schemas
from auth import (
    hash_password, verify_password, create_access_token,
    get_current_user, require_admin,
)
from database import get_db

app = FastAPI(
    title="BaryoBenta API",
    version="0.3.0",
    description="REST API of the BaryoBenta community buy & sell marketplace. "
                "Members register and log in for a JWT bearer token; protected "
                "endpoints expect 'Authorization: Bearer <token>'. The mobile "
                "client's PHP-style contract (*.php paths, X-Auth-Token header, "
                "envelope responses) is served by php_compat.",
)

# The Android client in android/ was built against the team's original PHP API:
# *.php paths, an X-Auth-Token session header and {"success","message","data"}
# envelopes. php_compat re-implements that contract on this deployment, so the
# shipped APK talks to the same cloud backend as the documented REST API.
import php_compat  # noqa: E402  (imported after the sys.path bootstrap above)

app.include_router(php_compat.router)


@app.get("/", include_in_schema=False)
def root():
    return {"message": "BaryoBenta API is running", "docs": "/docs", "health": "/health"}


def error_body(status_code: int, error: str, detail: str) -> dict:
    return {"error": error, "detail": detail, "status_code": status_code}


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content=error_body(422, "Validation Error", str(exc.errors())),
    )


@app.exception_handler(IntegrityError)
async def integrity_error_handler(request, exc):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=error_body(400, "Integrity Error",
                           "The request violates a database constraint (e.g. duplicate or invalid reference)."),
    )


@app.exception_handler(SQLAlchemyError)
async def db_error_handler(request, exc):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=error_body(500, "Database Error", "An unexpected database error occurred."),
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content=error_body(exc.status_code, "HTTP Error", exc.detail),
    )


# =============================================================== users / auth
@app.post("/users", response_model=schemas.UserOut, status_code=status.HTTP_201_CREATED,
          tags=["Users"], summary="Register User")
def register_user(payload: schemas.UserCreate, db: Session = Depends(get_db)):
    """UC-01: create a member account. The role may only be Buyer or Seller;
    the password is stored as a bcrypt hash, never as plain text."""
    existing = db.query(models.User).filter(models.User.email == payload.email).first()
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Email is already registered.")
    user = models.User(
        first_name=payload.first_name,
        last_name=payload.last_name,
        email=payload.email,
        password_hash=hash_password(payload.password),
        contact_number=payload.contact_number,
        role=payload.role.value,
        verified_status=False,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@app.post("/auth/login", response_model=schemas.TokenOut, tags=["Users"], summary="Log In")
def login(payload: schemas.LoginIn, db: Session = Depends(get_db)):
    """UC-04: verify the email and password and return a JWT bearer token
    that protected endpoints require."""
    user = db.query(models.User).filter(models.User.email == payload.email).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")
    return schemas.TokenOut(
        access_token=create_access_token(user.user_id, user.role),
        user_id=user.user_id,
        role=user.role,
    )


@app.get("/users/{user_id}", response_model=schemas.UserOut, tags=["Users"], summary="Get User")
def get_user(user_id: int, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.user_id == user_id).first()
    if not user:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"User {user_id} not found.")
    return user


@app.get("/users/{user_id}/ratings", response_model=list[schemas.RatingOut],
         tags=["Users"], summary="Get User Ratings")
def get_user_ratings(user_id: int, db: Session = Depends(get_db)):
    """Ratings received by a member, so buyers can judge a seller."""
    user = db.query(models.User).filter(models.User.user_id == user_id).first()
    if not user:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"User {user_id} not found.")
    return (db.query(models.Rating)
            .filter(models.Rating.ratee_id == user_id)
            .order_by(models.Rating.created_at.desc())
            .all())


# =================================================================== category
@app.post("/categories", response_model=schemas.CategoryOut, status_code=status.HTTP_201_CREATED,
          tags=["Categories"], summary="Create Category")
def create_category(payload: schemas.CategoryCreate, db: Session = Depends(get_db)):
    category = models.Category(category_name=payload.category_name)
    db.add(category)
    db.commit()
    db.refresh(category)
    return category


@app.get("/categories", response_model=list[schemas.CategoryOut],
         tags=["Categories"], summary="List Categories")
def list_categories(db: Session = Depends(get_db)):
    return db.query(models.Category).all()


# ==================================================================== listing
def _get_owned_listing(listing_id: int, current_user: models.User, db: Session) -> models.Listing:
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    if listing.seller_id != current_user.user_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Only the seller who owns this listing may do that.")
    return listing


@app.post("/listings", response_model=schemas.ListingOut, status_code=status.HTTP_201_CREATED,
          tags=["Listings"], summary="Create Listing")
def create_listing(payload: schemas.ListingCreate, db: Session = Depends(get_db),
                   current_user: models.User = Depends(get_current_user)):
    """UC-02: the logged-in member posts a listing as its seller."""
    category = db.query(models.Category).filter(models.Category.category_id == payload.category_id).first()
    if not category:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Category {payload.category_id} not found.")
    listing = models.Listing(seller_id=current_user.user_id, **payload.model_dump())
    db.add(listing)
    db.commit()
    db.refresh(listing)
    return listing


@app.get("/listings", response_model=list[schemas.ListingOut],
         tags=["Listings"], summary="Browse Listings")
def browse_listings(
    search: str | None = None,
    category_id: int | None = None,
    status_filter: schemas.ListingStatus = schemas.ListingStatus.Available,
    db: Session = Depends(get_db),
):
    query = db.query(models.Listing).filter(models.Listing.status == status_filter.value)
    if category_id is not None:
        query = query.filter(models.Listing.category_id == category_id)
    if search:
        pattern = f"%{search}%"
        query = query.filter(or_(models.Listing.title.ilike(pattern),
                                 models.Listing.description.ilike(pattern)))
    return query.order_by(models.Listing.created_at.desc()).all()


@app.get("/listings/{listing_id}", response_model=schemas.ListingOut,
         tags=["Listings"], summary="Get Listing")
def get_listing(listing_id: int, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    return listing


@app.patch("/listings/{listing_id}", response_model=schemas.ListingOut,
           tags=["Listings"], summary="Update Listing")
def update_listing(listing_id: int, payload: schemas.ListingUpdate, db: Session = Depends(get_db),
                   current_user: models.User = Depends(get_current_user)):
    """The seller who owns the listing may edit its details."""
    listing = _get_owned_listing(listing_id, current_user, db)
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(listing, field, value)
    db.commit()
    db.refresh(listing)
    return listing


@app.delete("/listings/{listing_id}", status_code=status.HTTP_204_NO_CONTENT,
            tags=["Listings"], summary="Delete Listing")
def delete_listing(listing_id: int, db: Session = Depends(get_db),
                   current_user: models.User = Depends(get_current_user)):
    """The owner (or an administrator) removes a listing."""
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    if listing.seller_id != current_user.user_id and current_user.role != "Admin":
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            detail="Only the seller or an administrator can delete this listing.")
    db.delete(listing)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/listings/{listing_id}/images", response_model=list[schemas.ListingImageOut],
         tags=["Listings"], summary="List Listing Images")
def list_listing_images(listing_id: int, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    return (db.query(models.ListingImage)
            .filter(models.ListingImage.listing_id == listing_id)
            .order_by(models.ListingImage.display_order)
            .all())


@app.post("/listings/{listing_id}/images", response_model=schemas.ListingImageOut,
          status_code=status.HTTP_201_CREATED, tags=["Listings"], summary="Add Listing Image")
def add_listing_image(listing_id: int, payload: schemas.ListingImageCreate, db: Session = Depends(get_db),
                      current_user: models.User = Depends(get_current_user)):
    """The seller attaches an image URL to their listing (at most 8 per listing)."""
    listing = _get_owned_listing(listing_id, current_user, db)
    count = db.query(models.ListingImage).filter(models.ListingImage.listing_id == listing_id).count()
    if count >= 8:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="A listing can have at most 8 images.")
    image = models.ListingImage(listing_id=listing.listing_id, **payload.model_dump())
    db.add(image)
    db.commit()
    db.refresh(image)
    return image


# ================================================================= transaction
def _get_transaction_with_listing(transaction_id: int, db: Session):
    txn = db.query(models.Transaction).filter(models.Transaction.transaction_id == transaction_id).first()
    if not txn:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Transaction {transaction_id} not found.")
    listing = db.query(models.Listing).filter(models.Listing.listing_id == txn.listing_id).first()
    return txn, listing


@app.post("/transactions", response_model=schemas.TransactionOut, status_code=status.HTTP_201_CREATED,
          tags=["Transactions"], summary="Record Transaction (Pending)")
def record_transaction(payload: schemas.TransactionCreate, db: Session = Depends(get_db),
                       current_user: models.User = Depends(get_current_user)):
    """UC-03: the logged-in buyer records the agreed meetup. The transaction
    starts as Pending and the listing stays Available until the seller
    completes it after the face-to-face exchange."""
    listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    if listing.status != "Available":
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is not available for purchase.")
    if listing.seller_id == current_user.user_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Sellers cannot buy their own listing.")
    open_txn = (db.query(models.Transaction)
                .filter(models.Transaction.listing_id == payload.listing_id,
                        models.Transaction.status == "Pending")
                .first())
    if open_txn:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="This listing already has a pending transaction.")
    txn = models.Transaction(
        listing_id=payload.listing_id,
        buyer_id=current_user.user_id,
        meetup_location=payload.meetup_location,
        meetup_datetime=payload.meetup_datetime,
        status="Pending",
    )
    db.add(txn)
    db.commit()
    db.refresh(txn)
    return txn


@app.get("/transactions", response_model=list[schemas.TransactionOut],
         tags=["Transactions"], summary="List My Transactions")
def list_my_transactions(db: Session = Depends(get_db),
                         current_user: models.User = Depends(get_current_user)):
    """Transactions of the logged-in member, as buyer or as seller."""
    seller_listing_ids = select(models.Listing.listing_id).where(
        models.Listing.seller_id == current_user.user_id)
    return (db.query(models.Transaction)
            .filter(or_(models.Transaction.buyer_id == current_user.user_id,
                        models.Transaction.listing_id.in_(seller_listing_ids)))
            .order_by(models.Transaction.created_at.desc())
            .all())


@app.patch("/transactions/{transaction_id}/complete", response_model=schemas.TransactionOut,
           tags=["Transactions"], summary="Complete Transaction")
def complete_transaction(transaction_id: int, db: Session = Depends(get_db),
                         current_user: models.User = Depends(get_current_user)):
    """UC-03: after the face-to-face exchange the seller marks the Pending
    transaction Completed and the listing becomes Sold."""
    txn, listing = _get_transaction_with_listing(transaction_id, db)
    if listing.seller_id != current_user.user_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            detail="Only the seller of the listing can complete the transaction.")
    if txn.status != "Pending":
        raise HTTPException(status.HTTP_400_BAD_REQUEST,
                            detail=f"Only a Pending transaction can be completed (current status: {txn.status}).")
    txn.status = "Completed"
    listing.status = "Sold"
    db.commit()
    db.refresh(txn)
    return txn


@app.patch("/transactions/{transaction_id}/cancel", response_model=schemas.TransactionOut,
           tags=["Transactions"], summary="Cancel Transaction")
def cancel_transaction(transaction_id: int, db: Session = Depends(get_db),
                       current_user: models.User = Depends(get_current_user)):
    """UC-03 alternative flow: the buyer or the seller cancels a Pending
    meetup; the listing stays Available."""
    txn, listing = _get_transaction_with_listing(transaction_id, db)
    if current_user.user_id not in (txn.buyer_id, listing.seller_id):
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            detail="Only the buyer or the seller can cancel this transaction.")
    if txn.status != "Pending":
        raise HTTPException(status.HTTP_400_BAD_REQUEST,
                            detail=f"Only a Pending transaction can be cancelled (current status: {txn.status}).")
    txn.status = "Cancelled"
    db.commit()
    db.refresh(txn)
    return txn


# =================================================================== favorite
@app.post("/favorites", response_model=schemas.FavoriteOut, status_code=status.HTTP_201_CREATED,
          tags=["Favorites"], summary="Save Favorite")
def save_favorite(payload: schemas.FavoriteCreate, db: Session = Depends(get_db),
                  current_user: models.User = Depends(get_current_user)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    already = (db.query(models.Favorite)
               .filter(models.Favorite.user_id == current_user.user_id,
                       models.Favorite.listing_id == payload.listing_id)
               .first())
    if already:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is already in favorites.")
    favorite = models.Favorite(user_id=current_user.user_id, listing_id=payload.listing_id)
    db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


@app.get("/favorites", response_model=list[schemas.FavoriteOut],
         tags=["Favorites"], summary="List My Favorites")
def list_my_favorites(db: Session = Depends(get_db),
                      current_user: models.User = Depends(get_current_user)):
    return (db.query(models.Favorite)
            .filter(models.Favorite.user_id == current_user.user_id)
            .order_by(models.Favorite.created_at.desc())
            .all())


@app.delete("/favorites/{listing_id}", status_code=status.HTTP_204_NO_CONTENT,
            tags=["Favorites"], summary="Remove Favorite")
def remove_favorite(listing_id: int, db: Session = Depends(get_db),
                    current_user: models.User = Depends(get_current_user)):
    favorite = (db.query(models.Favorite)
                .filter(models.Favorite.user_id == current_user.user_id,
                        models.Favorite.listing_id == listing_id)
                .first())
    if not favorite:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Favorite not found.")
    db.delete(favorite)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ==================================================================== message
@app.post("/messages", response_model=schemas.MessageOut, status_code=status.HTTP_201_CREATED,
          tags=["Messages"], summary="Send Message")
def send_message(payload: schemas.MessageCreate, db: Session = Depends(get_db),
                 current_user: models.User = Depends(get_current_user)):
    """Chat used to arrange the meetup (UC-03 basic flow steps 1-2)."""
    if payload.receiver_id == current_user.user_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="You cannot message yourself.")
    receiver = db.query(models.User).filter(models.User.user_id == payload.receiver_id).first()
    if not receiver:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Receiver {payload.receiver_id} not found.")
    if payload.listing_id is not None:
        listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
        if not listing:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    message = models.Message(sender_id=current_user.user_id, **payload.model_dump())
    db.add(message)
    db.commit()
    db.refresh(message)
    return message


@app.get("/messages/{peer_id}", response_model=list[schemas.MessageOut],
         tags=["Messages"], summary="Get Conversation")
def get_conversation(peer_id: int, listing_id: int | None = None, db: Session = Depends(get_db),
                     current_user: models.User = Depends(get_current_user)):
    """Messages between the logged-in member and one other member."""
    query = db.query(models.Message).filter(
        or_(and_(models.Message.sender_id == current_user.user_id,
                 models.Message.receiver_id == peer_id),
            and_(models.Message.sender_id == peer_id,
                 models.Message.receiver_id == current_user.user_id)))
    if listing_id is not None:
        query = query.filter(models.Message.listing_id == listing_id)
    return query.order_by(models.Message.sent_at).all()


# ===================================================================== rating
@app.post("/ratings", response_model=schemas.RatingOut, status_code=status.HTTP_201_CREATED,
          tags=["Ratings"], summary="Submit Rating")
def submit_rating(payload: schemas.RatingCreate, db: Session = Depends(get_db),
                  current_user: models.User = Depends(get_current_user)):
    """Only a participant of a Completed transaction may rate, once each.
    The ratee is always the other participant."""
    txn = db.query(models.Transaction).filter(models.Transaction.transaction_id == payload.transaction_id).first()
    if not txn:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Transaction {payload.transaction_id} not found.")
    if txn.status != "Completed":
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Only completed transactions can be rated.")
    listing = db.query(models.Listing).filter(models.Listing.listing_id == txn.listing_id).first()
    if current_user.user_id == txn.buyer_id:
        ratee_id = listing.seller_id
    elif current_user.user_id == listing.seller_id:
        ratee_id = txn.buyer_id
    else:
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            detail="Only the buyer or the seller of this transaction can rate it.")
    existing = (db.query(models.Rating)
                .filter(models.Rating.transaction_id == payload.transaction_id,
                        models.Rating.rater_id == current_user.user_id)
                .first())
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="You have already rated this transaction.")
    rating = models.Rating(
        transaction_id=payload.transaction_id,
        rater_id=current_user.user_id,
        ratee_id=ratee_id,
        score=payload.score,
        comment=payload.comment,
    )
    db.add(rating)
    db.commit()
    db.refresh(rating)
    return rating


# ====================================================================== boost
@app.post("/boosts", response_model=schemas.BoostOut, status_code=status.HTTP_201_CREATED,
          tags=["Boosts"], summary="Request Listing Boost")
def request_boost(payload: schemas.BoostCreate, db: Session = Depends(get_db),
                  current_user: models.User = Depends(get_current_user)):
    """The seller of a listing requests a boost. The fee is settled offline
    (for example cash or GCash between the seller and the administrator); the
    app never processes payment. The request stays payment_status Pending
    until an administrator confirms it."""
    _get_owned_listing(payload.listing_id, current_user, db)
    active = (db.query(models.Boost)
              .filter(models.Boost.listing_id == payload.listing_id,
                      models.Boost.payment_status.in_(["Pending", "Confirmed"]))
              .first())
    if active:
        raise HTTPException(status.HTTP_400_BAD_REQUEST,
                            detail="This listing already has a pending or active boost request.")
    boost = models.Boost(listing_id=payload.listing_id,
                         amount=payload.amount,
                         start_date=payload.start_date,
                         end_date=payload.end_date,
                         payment_status="Pending")
    db.add(boost)
    db.commit()
    db.refresh(boost)
    return boost


@app.get("/boosts", response_model=list[schemas.BoostOut], tags=["Boosts"],
         summary="List Boost Requests")
def list_boosts(listing_id: int | None = None, db: Session = Depends(get_db),
                current_user: models.User = Depends(get_current_user)):
    """Administrators see every request; sellers see only their own."""
    query = db.query(models.Boost)
    if listing_id is not None:
        query = query.filter(models.Boost.listing_id == listing_id)
    if current_user.role != "Admin":
        own_listings = select(models.Listing.listing_id).where(
            models.Listing.seller_id == current_user.user_id)
        query = query.filter(models.Boost.listing_id.in_(own_listings))
    return query.order_by(models.Boost.boost_id.desc()).all()


@app.patch("/boosts/{boost_id}", response_model=schemas.BoostOut, tags=["Boosts"],
           summary="Review Boost Request (Admin)")
def review_boost(boost_id: int, payload: schemas.BoostReview, db: Session = Depends(get_db),
                 admin: models.User = Depends(require_admin)):
    """An administrator confirms the boost after the offline payment is
    received, or rejects the request."""
    boost = db.query(models.Boost).filter(models.Boost.boost_id == boost_id).first()
    if not boost:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Boost {boost_id} not found.")
    if boost.payment_status != "Pending":
        raise HTTPException(status.HTTP_400_BAD_REQUEST,
                            detail=f"This request was already reviewed ({boost.payment_status}).")
    if payload.payment_status == schemas.BoostPaymentStatus.Pending:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Set the request to Confirmed or Rejected.")
    boost.payment_status = payload.payment_status.value
    db.commit()
    db.refresh(boost)
    return boost


# ===================================================================== report
@app.post("/reports", response_model=schemas.ReportOut, status_code=status.HTTP_201_CREATED,
          tags=["Reports"], summary="Submit Report")
def submit_report(payload: schemas.ReportCreate, db: Session = Depends(get_db),
                  current_user: models.User = Depends(get_current_user)):
    """A member reports a problem listing, a user, or both."""
    if payload.listing_id is not None:
        listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
        if not listing:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    if payload.reported_user_id is not None:
        reported = db.query(models.User).filter(models.User.user_id == payload.reported_user_id).first()
        if not reported:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"User {payload.reported_user_id} not found.")
        if reported.user_id == current_user.user_id:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="You cannot report yourself.")
    report = models.Report(reporter_id=current_user.user_id, **payload.model_dump())
    db.add(report)
    db.commit()
    db.refresh(report)
    return report


@app.get("/reports", response_model=list[schemas.ReportOut], tags=["Reports"],
         summary="List Reports (Admin)")
def list_reports(status_filter: schemas.ReportStatus | None = None, db: Session = Depends(get_db),
                 admin: models.User = Depends(require_admin)):
    query = db.query(models.Report)
    if status_filter is not None:
        query = query.filter(models.Report.status == status_filter.value)
    return query.order_by(models.Report.created_at.desc()).all()


@app.patch("/reports/{report_id}", response_model=schemas.ReportOut, tags=["Reports"],
           summary="Review Report (Admin)")
def review_report(report_id: int, payload: schemas.ReportReview, db: Session = Depends(get_db),
                  admin: models.User = Depends(require_admin)):
    """An administrator resolves or dismisses a report."""
    report = db.query(models.Report).filter(models.Report.report_id == report_id).first()
    if not report:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Report {report_id} not found.")
    report.status = payload.status.value
    db.commit()
    db.refresh(report)
    return report


# ======================================================== exports and system
@app.get("/categories/export/csv", tags=["System"], summary="Export Category Summary CSV")
def export_category_summary_csv(db: Session = Depends(get_db)):
    rows = db.execute(
        text(
            "SELECT c.category_name, COUNT(l.listing_id) AS total_listings "
            "FROM category c "
            "LEFT JOIN listing l ON l.category_id = c.category_id "
            "GROUP BY c.category_name "
            "ORDER BY c.category_name"
        )
    ).all()
    buffer = StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["category_name", "total_listings"])
    for row in rows:
        writer.writerow([row.category_name, row.total_listings])
    buffer.seek(0)
    return Response(
        content=buffer.getvalue(),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=category_summary.csv"},
    )


@app.get("/health", tags=["System"], summary="Health Check")
def health_check():
    return {"status": "ok"}

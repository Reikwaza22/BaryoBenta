from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy import text

# For CSV export
import csv
from io import StringIO

import models
import schemas
from database import engine, get_db, Base

# Creates tables if they don't exist yet (safe alongside schema.sql, which
# already creates them with ENUM types / indexes for the live Supabase DB).
Base.metadata.create_all(bind=engine)

app = FastAPI(title="BaryoBenta API", version="0.1.0")


# ------------------------------------------------------------------
# Centralized, structured error handling
# ------------------------------------------------------------------
def error_body(status_code: int, error: str, detail: str) -> dict:
    return {"error": error, "detail": detail, "status_code": status_code}


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    """Catches malformed request bodies/query/path params (422)."""
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content=error_body(422, "Validation Error", str(exc.errors())),
    )


@app.exception_handler(IntegrityError)
async def integrity_error_handler(request, exc):
    """Catches DB constraint violations: duplicate email, duplicate
    favorite pair, missing FK target, invalid rating range, etc."""
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=error_body(400, "Integrity Error", "The request violates a database constraint (e.g. duplicate or invalid reference)."),
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


# ------------------------------------------------------------------
# USER
# ------------------------------------------------------------------
@app.post("/users", response_model=schemas.UserOut, status_code=status.HTTP_201_CREATED, tags=["Users"])
def register_user(payload: schemas.UserCreate, db: Session = Depends(get_db)):
    existing = db.query(models.User).filter(models.User.email == payload.email).first()
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Email is already registered.")

    # NOTE: replace with a real hash (e.g. passlib bcrypt) before production use.
    fake_hash = f"hashed:{payload.password}"

    user = models.User(
        full_name=payload.full_name,
        email=payload.email,
        password_hash=fake_hash,
        phone_number=payload.phone_number,
        role=payload.role,
        campus_location=payload.campus_location,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@app.get("/users/{user_id}", response_model=schemas.UserOut, tags=["Users"])
def get_user(user_id: int, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.user_id == user_id).first()
    if not user:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"User {user_id} not found.")
    return user


# ------------------------------------------------------------------
# CATEGORY
# ------------------------------------------------------------------
@app.post("/categories", response_model=schemas.CategoryOut, status_code=status.HTTP_201_CREATED, tags=["Categories"])
def create_category(payload: schemas.CategoryCreate, db: Session = Depends(get_db)):
    category = models.Category(category_name=payload.category_name)
    db.add(category)
    db.commit()
    db.refresh(category)
    return category


@app.get("/categories", response_model=list[schemas.CategoryOut], tags=["Categories"])
def list_categories(db: Session = Depends(get_db)):
    return db.query(models.Category).all()


# ------------------------------------------------------------------
# LISTING
# ------------------------------------------------------------------
@app.post("/listings", response_model=schemas.ListingOut, status_code=status.HTTP_201_CREATED, tags=["Listings"])
def create_listing(payload: schemas.ListingCreate, db: Session = Depends(get_db)):
    seller = db.query(models.User).filter(models.User.user_id == payload.seller_id).first()
    if not seller:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Seller {payload.seller_id} not found.")

    category = db.query(models.Category).filter(models.Category.category_id == payload.category_id).first()
    if not category:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Category {payload.category_id} not found.")

    listing = models.Listing(**payload.model_dump())
    db.add(listing)
    db.commit()
    db.refresh(listing)
    return listing


@app.get("/listings", response_model=list[schemas.ListingOut], tags=["Listings"])
def browse_listings(
    category_id: int | None = None,
    status_filter: schemas.ListingStatus = schemas.ListingStatus.Available,
    db: Session = Depends(get_db),
):
    query = db.query(models.Listing).filter(models.Listing.status == status_filter)
    if category_id is not None:
        query = query.filter(models.Listing.category_id == category_id)
    return query.all()


@app.get("/listings/{listing_id}", response_model=schemas.ListingOut, tags=["Listings"])
def get_listing(listing_id: int, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    return listing


# ------------------------------------------------------------------
# TRANSACTION
# ------------------------------------------------------------------
@app.post("/transactions", response_model=schemas.TransactionOut, status_code=status.HTTP_201_CREATED, tags=["Transactions"])
def complete_transaction(payload: schemas.TransactionCreate, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    if listing.status != models.ListingStatus.Available:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is not available for purchase.")

    buyer = db.query(models.User).filter(models.User.user_id == payload.buyer_id).first()
    if not buyer:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Buyer {payload.buyer_id} not found.")

    txn = models.Transaction(
        listing_id=payload.listing_id,
        buyer_id=payload.buyer_id,
        amount=payload.amount,
        payment_method=payload.payment_method,
        status=models.TransactionStatus.Completed,
    )
    listing.status = models.ListingStatus.Sold
    db.add(txn)
    db.commit()
    db.refresh(txn)
    return txn


# ------------------------------------------------------------------
# FAVORITE
# ------------------------------------------------------------------
@app.post("/favorites", response_model=schemas.FavoriteOut, status_code=status.HTTP_201_CREATED, tags=["Favorites"])
def save_favorite(payload: schemas.FavoriteCreate, db: Session = Depends(get_db)):
    already = (
        db.query(models.Favorite)
        .filter(models.Favorite.user_id == payload.user_id, models.Favorite.listing_id == payload.listing_id)
        .first()
    )
    if already:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is already in favorites.")

    favorite = models.Favorite(**payload.model_dump())
    db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


# ------------------------------------------------------------------
# REVIEW
# ------------------------------------------------------------------
@app.post("/reviews", response_model=schemas.ReviewOut, status_code=status.HTTP_201_CREATED, tags=["Reviews"])
def submit_review(payload: schemas.ReviewCreate, db: Session = Depends(get_db)):
    txn = db.query(models.Transaction).filter(models.Transaction.transaction_id == payload.transaction_id).first()
    if not txn:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Transaction {payload.transaction_id} not found.")
    if txn.status != models.TransactionStatus.Completed:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Only completed transactions can be reviewed.")

    existing = db.query(models.Review).filter(models.Review.transaction_id == payload.transaction_id).first()
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="This transaction has already been reviewed.")

    review = models.Review(**payload.model_dump())
    db.add(review)
    db.commit()
    db.refresh(review)
    return review


# ------------------------------------------------------------------
# REPORTS — CSV export
# ------------------------------------------------------------------
@app.get("/categories/export/csv", tags=["Reports"])
def export_category_summary_csv(db: Session = Depends(get_db)):
    """Export a per-category listing count report as a downloadable CSV file."""
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


@app.get("/health", tags=["System"])
def health_check():
    return {"status": "ok"}

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy import text

import csv
from io import StringIO

import models
import schemas
from database import get_db

app = FastAPI(title="BaryoBenta API", version="0.1.0")


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


@app.post("/users", response_model=schemas.UserOut, status_code=status.HTTP_201_CREATED, tags=["Users"])
def register_user(payload: schemas.UserCreate, db: Session = Depends(get_db)):
    existing = db.query(models.User).filter(models.User.email == payload.email).first()
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Email is already registered.")
    user = models.User(
        first_name=payload.first_name,
        last_name=payload.last_name,
        email=payload.email,
        password_hash=f"hashed:{payload.password}",
        contact_number=payload.contact_number,
        role=payload.role,
        verified_status=False,
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
    query = db.query(models.Listing).filter(models.Listing.status == status_filter.value)
    if category_id is not None:
        query = query.filter(models.Listing.category_id == category_id)
    return query.all()


@app.get("/listings/{listing_id}", response_model=schemas.ListingOut, tags=["Listings"])
def get_listing(listing_id: int, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {listing_id} not found.")
    return listing


@app.post("/transactions", response_model=schemas.TransactionOut, status_code=status.HTTP_201_CREATED, tags=["Transactions"])
def complete_transaction(payload: schemas.TransactionCreate, db: Session = Depends(get_db)):
    listing = db.query(models.Listing).filter(models.Listing.listing_id == payload.listing_id).first()
    if not listing:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Listing {payload.listing_id} not found.")
    if listing.status != "Available":
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is not available for purchase.")
    buyer = db.query(models.User).filter(models.User.user_id == payload.buyer_id).first()
    if not buyer:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Buyer {payload.buyer_id} not found.")
    txn = models.Transaction(
        listing_id=payload.listing_id,
        buyer_id=payload.buyer_id,
        meetup_location=payload.meetup_location,
        meetup_datetime=payload.meetup_datetime,
        status="Completed",
    )
    listing.status = "Sold"
    db.add(txn)
    db.commit()
    db.refresh(txn)
    return txn


@app.post("/favorites", response_model=schemas.FavoriteOut, status_code=status.HTTP_201_CREATED, tags=["Favorites"])
def save_favorite(payload: schemas.FavoriteCreate, db: Session = Depends(get_db)):
    already = (
        db.query(models.Favorite)
        .filter(models.Favorite.user_id == payload.user_id,
                models.Favorite.listing_id == payload.listing_id)
        .first()
    )
    if already:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Listing is already in favorites.")
    favorite = models.Favorite(user_id=payload.user_id, listing_id=payload.listing_id)
    db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


@app.post("/ratings", response_model=schemas.RatingOut, status_code=status.HTTP_201_CREATED, tags=["Ratings"])
def submit_rating(payload: schemas.RatingCreate, db: Session = Depends(get_db)):
    txn = db.query(models.Transaction).filter(models.Transaction.transaction_id == payload.transaction_id).first()
    if not txn:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Transaction {payload.transaction_id} not found.")
    if txn.status != "Completed":
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Only completed transactions can be rated.")
    existing = (
        db.query(models.Rating)
        .filter(models.Rating.transaction_id == payload.transaction_id,
                models.Rating.rater_id == payload.rater_id)
        .first()
    )
    if existing:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="You have already rated this transaction.")
    rating = models.Rating(**payload.model_dump())
    db.add(rating)
    db.commit()
    db.refresh(rating)
    return rating


@app.get("/categories/export/csv", tags=["Reports"])
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


@app.get("/health", tags=["System"])
def health_check():
    return {"status": "ok"}

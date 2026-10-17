package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A marketplace listing exactly as stored in PostgreSQL (via listings.php / listing.php). */
public class Listing {
    @SerializedName("listing_id") private int id;
    @SerializedName("seller_id") private int sellerId;
    private String title;
    private String description;
    private double price;
    @SerializedName("category_id") private int categoryId;
    private String category;
    private String seller;
    @SerializedName("seller_verified") private boolean sellerVerified;
    private String status;
    private float rating;
    @SerializedName("review_count") private int reviewCount;
    @SerializedName("image_url") private String imageUrl;
    @SerializedName("is_favorite") private boolean favorite;
    @SerializedName("is_owner") private boolean owner;
    @SerializedName("created_at") private String createdAt;
    private List<Spec> specs;

    public int getId() { return id; }
    public int getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description == null ? "" : description; }
    public double getPrice() { return price; }
    public String getPriceLabel() { return Fmt.peso(price); }
    public int getCategoryId() { return categoryId; }
    public String getCategory() { return category; }
    public String getSeller() { return seller; }
    public boolean isSellerVerified() { return sellerVerified; }
    public String getStatus() { return status == null ? "available" : status; }
    public float getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public String getImageUrl() { return imageUrl; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public boolean isOwner() { return owner; }
    public String getCreatedAt() { return createdAt; }
    public List<Spec> getSpecs() { return specs; }
}

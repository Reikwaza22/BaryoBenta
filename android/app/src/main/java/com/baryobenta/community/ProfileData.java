package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

/** Response of profile.php (own profile or another seller's public profile). */
public class ProfileData {
    @SerializedName("user_id") private int userId;
    @SerializedName("full_name") private String fullName;
    private String username;
    private String email;
    private String location;
    private boolean verified;
    @SerializedName("kyc_status") private String kycStatus;
    @SerializedName("listing_count") private int listingCount;
    @SerializedName("sold_count") private int soldCount;
    private float rating;
    @SerializedName("review_count") private int reviewCount;

    public int getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }
    public boolean isVerified() { return verified; }
    public String getKycStatus() { return kycStatus == null ? "none" : kycStatus; }
    public int getListingCount() { return listingCount; }
    public int getSoldCount() { return soldCount; }
    public float getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
}

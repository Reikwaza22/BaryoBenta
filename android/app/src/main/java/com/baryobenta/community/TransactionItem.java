package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

public class TransactionItem {
    @SerializedName("transaction_id") private int id;
    @SerializedName("listing_id") private int listingId;
    private String title;
    private double amount;
    @SerializedName("created_at") private String createdAt;
    private String role;
    @SerializedName("other_name") private String otherName;

    public int getId() { return id; }
    public int getListingId() { return listingId; }
    public String getTitle() { return title; }
    public double getAmount() { return amount; }
    public String getCreatedAt() { return createdAt; }
    public boolean isSold() { return "sold".equals(role); }
    public String getOtherName() { return otherName; }
}

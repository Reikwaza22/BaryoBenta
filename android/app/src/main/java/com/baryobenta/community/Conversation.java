package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

/** One chat thread = one listing + one other person. */
public class Conversation {
    @SerializedName("listing_id") private int listingId;
    @SerializedName("other_id") private int otherId;
    @SerializedName("other_name") private String otherName;
    @SerializedName("other_verified") private boolean otherVerified;
    @SerializedName("listing_title") private String listingTitle;
    @SerializedName("last_message") private String lastMessage;
    @SerializedName("created_at") private String createdAt;
    private int unread;

    public int getListingId() { return listingId; }
    public int getOtherId() { return otherId; }
    public String getOtherName() { return otherName; }
    public boolean isOtherVerified() { return otherVerified; }
    public String getListingTitle() { return listingTitle; }
    public String getLastMessage() { return lastMessage; }
    public String getCreatedAt() { return createdAt; }
    public int getUnread() { return unread; }
}

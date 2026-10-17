package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

public class BuyerItem {
    @SerializedName("user_id") private int userId;
    private String name;

    public int getUserId() { return userId; }
    public String getName() { return name; }
}

package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

public class CommentItem {
    @SerializedName("comment_id") private int id;
    @SerializedName("user_id") private int userId;
    private String name;
    private boolean verified;
    private String body;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("is_mine") private boolean mine;

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public boolean isVerified() { return verified; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
    public boolean isMine() { return mine; }
}

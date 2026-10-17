package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatItem {
    @SerializedName("message_id") private int id;
    private String body;
    private String type;
    @SerializedName("photo_url") private String photoUrl;
    private int duration;
    private String reaction;
    @SerializedName("reaction_mine") private boolean reactionMine;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("is_mine") private boolean mine;

    public int getId() { return id; }
    public String getBody() { return body; }
    public String getType() { return type == null ? "text" : type; }
    public String getPhotoUrl() { return photoUrl; }
    public int getDuration() { return duration; }
    public String getReaction() { return reaction == null ? "" : reaction; }
    public boolean isReactionMine() { return reactionMine; }
    public String getCreatedAt() { return createdAt; }
    public boolean isMine() { return mine; }

    public boolean isPhoto() { return "photo".equals(getType()); }
    public boolean isVoice() { return "voice".equals(getType()); }
}

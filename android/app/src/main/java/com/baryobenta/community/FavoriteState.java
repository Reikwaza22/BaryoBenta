package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

public class FavoriteState {
    @SerializedName("is_favorite") private boolean favorite;

    public boolean isFavorite() { return favorite; }
}

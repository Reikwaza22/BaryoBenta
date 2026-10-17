package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

/** A marketplace category row from PostgreSQL. id == 0 is the local "All" pseudo-category. */
public class Category {
    @SerializedName("category_id") private int id;
    private String name;
    @SerializedName("icon_key") private String iconKey;

    public Category(int id, String name, String iconKey) {
        this.id = id;
        this.name = name;
        this.iconKey = iconKey;
    }

    public int getId() { return id; }
    public String getName() { return name; }

    public int getIconResId() {
        if (iconKey == null) return R.drawable.ic_cat_others;
        switch (iconKey) {
            case "electronics": return R.drawable.ic_cat_electronics;
            case "fashion": return R.drawable.ic_cat_fashion;
            case "home": return R.drawable.ic_cat_home;
            case "sports": return R.drawable.ic_cat_sports;
            case "vehicles": return R.drawable.ic_cat_vehicles;
            case "all": return R.drawable.ic_category;
            default: return R.drawable.ic_cat_others;
        }
    }
}

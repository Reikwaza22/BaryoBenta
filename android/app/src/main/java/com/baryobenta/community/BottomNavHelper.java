package com.baryobenta.community;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Wires up click handling for the reusable bottom navigation bar
 * (layout_bottom_nav.xml) that is included on Home, Browse Listings,
 * and Profile. Also tints the icon/label for whichever tab is currently
 * active so navigation state stays visually consistent across screens.
 */
public final class BottomNavHelper {

    public enum Tab { HOME, BROWSE, SELL, MESSAGES, PROFILE }

    private BottomNavHelper() {
    }

    public static void setup(Activity activity, Tab activeTab) {
        activity.findViewById(R.id.nav_home).setOnClickListener(v -> {
            if (activeTab != Tab.HOME) {
                navigate(activity, activeTab, Tab.HOME, DashboardActivity.class);
            }
        });

        activity.findViewById(R.id.nav_browse).setOnClickListener(v -> {
            if (activeTab != Tab.BROWSE) {
                navigate(activity, activeTab, Tab.BROWSE, ListingActivity.class);
            }
        });

        activity.findViewById(R.id.nav_sell).setOnClickListener(v ->
                navigate(activity, activeTab, Tab.SELL, AddListingActivity.class));

        activity.findViewById(R.id.nav_messages).setOnClickListener(v -> {
            if (activeTab != Tab.MESSAGES) {
                navigate(activity, activeTab, Tab.MESSAGES, MessagesActivity.class);
            }
        });

        activity.findViewById(R.id.nav_profile).setOnClickListener(v -> {
            if (activeTab != Tab.PROFILE) {
                navigate(activity, activeTab, Tab.PROFILE, ProfileActivity.class);
            }
        });

        highlight(activity, R.id.icon_nav_home, R.id.label_nav_home, activeTab == Tab.HOME);
        highlight(activity, R.id.icon_nav_browse, R.id.label_nav_browse, activeTab == Tab.BROWSE);
        highlight(activity, R.id.icon_nav_messages, R.id.label_nav_messages, activeTab == Tab.MESSAGES);
        highlight(activity, R.id.icon_nav_profile, R.id.label_nav_profile, activeTab == Tab.PROFILE);
    }

    static void navigate(Activity activity, Tab from, Tab to,
                         Class<? extends Activity> destination) {
        activity.startActivity(new Intent(activity, destination));
        if (to == Tab.BROWSE || to == Tab.SELL || to == Tab.MESSAGES) {
            activity.overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out);
            return;
        }
        // Home and Profile keep a short horizontal transition.
        boolean movingRight = position(to) > position(from);
        activity.overridePendingTransition(
                movingRight ? R.anim.slide_in_from_right : R.anim.slide_in_from_left,
                movingRight ? R.anim.slide_out_to_left : R.anim.slide_out_to_right);
    }

    private static int position(Tab tab) {
        switch (tab) {
            case HOME: return 0;
            case BROWSE: return 1;
            case SELL: return 2;
            case MESSAGES: return 3;
            case PROFILE: return 4;
            default: return 0;
        }
    }

    private static void highlight(Activity activity, int iconId, int labelId, boolean active) {
        int color = active
                ? activity.getResources().getColor(R.color.primary)
                : activity.getResources().getColor(R.color.text_secondary);

        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        icon.setColorFilter(color);
        label.setTextColor(color);
    }
}

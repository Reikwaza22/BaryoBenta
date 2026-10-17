package com.baryobenta.community;

import android.content.Context;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small formatting helpers shared by the screens. */
public final class Fmt {

    private Fmt() {
    }

    public static String peso(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return "\u20B1" + nf.format(value);
    }

    /** Server timestamps are stored in UTC ("yyyy-MM-dd HH:mm:ss[.ffffff]"). */
    private static Date parse(String ts) {
        if (ts == null || ts.length() < 19) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(ts.substring(0, 19));
        } catch (ParseException e) {
            return null;
        }
    }

    public static String timeAgo(String ts) {
        Date d = parse(ts);
        if (d == null) return "";
        long diff = System.currentTimeMillis() - d.getTime();
        if (diff < 0) diff = 0;
        long min = diff / 60000L;
        if (min < 1) return "just now";
        if (min < 60) return min + "m ago";
        long hours = min / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return new SimpleDateFormat("MMM d", Locale.US).format(d);
    }

    public static String dateOnly(String ts) {
        Date d = parse(ts);
        return d == null ? "" : new SimpleDateFormat("MMM d, yyyy", Locale.US).format(d);
    }

    public static String statusLabel(Context context, String status) {
        if ("sold".equals(status)) return context.getString(R.string.status_sold);
        if ("pending".equals(status)) return context.getString(R.string.status_pending);
        return context.getString(R.string.status_available);
    }

    /** Applies the chip background/text color for a listing status to a TextView. */
    public static void styleStatus(android.widget.TextView view, String status) {
        Context c = view.getContext();
        view.setText(statusLabel(c, status));
        if ("sold".equals(status)) {
            view.setBackgroundResource(R.drawable.bg_status_sold);
            view.setTextColor(c.getResources().getColor(R.color.status_sold));
        } else if ("pending".equals(status)) {
            view.setBackgroundResource(R.drawable.bg_status_pending);
            view.setTextColor(c.getResources().getColor(R.color.status_pending));
        } else {
            view.setBackgroundResource(R.drawable.bg_status_chip);
            view.setTextColor(c.getResources().getColor(R.color.status_available));
        }
    }
}

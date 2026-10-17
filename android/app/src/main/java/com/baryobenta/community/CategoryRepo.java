package com.baryobenta.community;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Loads the category list from PostgreSQL once and keeps it in memory. */
public final class CategoryRepo {

    public interface Listener {
        void onLoaded(List<Category> categories);

        void onError(String message);
    }

    private static List<Category> cache;

    private CategoryRepo() {
    }

    /** Returns the categories, optionally with a leading "All" entry (id 0). */
    public static void load(final Context context, final boolean withAll, final Listener listener) {
        if (cache != null) {
            listener.onLoaded(build(context, withAll));
            return;
        }
        ApiHelper.call(context, RetrofitClient.getApi(context).categories(),
                new ApiHelper.Result<List<Category>>() {
                    @Override
                    public void onSuccess(List<Category> data, String message) {
                        cache = data == null ? new ArrayList<Category>() : data;
                        listener.onLoaded(build(context, withAll));
                    }

                    @Override
                    public void onError(String message) {
                        listener.onError(message);
                    }
                });
    }

    private static List<Category> build(Context context, boolean withAll) {
        List<Category> list = new ArrayList<>();
        if (withAll) {
            list.add(new Category(0, context.getString(R.string.category_all), "all"));
        }
        list.addAll(cache);
        return list;
    }

    public static void clear() {
        cache = null;
    }
}

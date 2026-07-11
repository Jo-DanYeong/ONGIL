package com.project.ongil.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class SavedPlaceStore {
    public static final String HOME = "home";
    public static final String ACADEMY = "academy";
    public static final String SCHOOL = "school";

    private static final String PREFS = "ongil_saved_places";

    private SavedPlaceStore() {}

    public static String getAddress(Context context, String type) {
        return prefs(context).getString(type + "_address", "");
    }

    public static void saveAddress(Context context, String type, String address) {
        String normalized = address.trim();
        SharedPreferences preferences = prefs(context);
        String previous = preferences.getString(type + "_address", "");
        SharedPreferences.Editor editor = preferences.edit().putString(type + "_address", normalized);
        if (!normalized.equals(previous)) {
            editor.remove(type + "_lat").remove(type + "_lon").remove(type + "_has_point");
        }
        editor.apply();
    }

    public static void savePoint(Context context, String type, double latitude, double longitude) {
        prefs(context).edit()
                .putLong(type + "_lat", Double.doubleToRawLongBits(latitude))
                .putLong(type + "_lon", Double.doubleToRawLongBits(longitude))
                .putBoolean(type + "_has_point", true)
                .apply();
    }

    public static boolean hasPoint(Context context, String type) {
        return prefs(context).getBoolean(type + "_has_point", false);
    }

    public static double getLatitude(Context context, String type) {
        return Double.longBitsToDouble(prefs(context).getLong(type + "_lat", 0));
    }

    public static double getLongitude(Context context, String type) {
        return Double.longBitsToDouble(prefs(context).getLong(type + "_lon", 0));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

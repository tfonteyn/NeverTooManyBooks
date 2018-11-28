package com.eleybourn.bookcatalogue.utils;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

public class BundleUtils {
    /**
     * Check if passed Bundle contains a non-blank string .
     *
     * @param bundle to check
     * @param key    to check for
     *
     * @return Present/absent
     */
    public static boolean isNonBlankString(final @NonNull Bundle bundle,
                                           final @NonNull String key) {
        String s = bundle.getString(key);
        return (s != null && !s.trim().isEmpty());
    }

    /**
     * Add the value to the Bundle if not present
     *
     * @param bundle to check
     * @param key    for data to add
     */
    public static void addIfNotPresent(final @NonNull Bundle bundle,
                                       final @NonNull String key,
                                       final @NonNull String value) {
        String test = bundle.getString(key);
        if (test == null || test.isEmpty()) {
            bundle.putString(key, value.trim());
        }
    }

    /**
     * Get a string from a Bundle
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or null when not found
     */
    public static String getStringFromBundles(final String key, final Bundle... bundles) {
        String value;
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.containsKey(key)) {
                value = bundle.getString(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Get a int from a Bundle
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or 0 when not found
     */
    public static int getIntFromBundles(final String key, final @NonNull Bundle... bundles) {
        int value;
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.containsKey(key)) {
                value = bundle.getInt(key, -1);
                if (value != -1) {
                    return value;
                }
            }
        }
        return 0;
    }

    /**
     * Get a long from a Bundle
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or 0 when not found
     */
    public static long getLongFromBundles(final @Nullable String key, final @NonNull Bundle... bundles) {
        long value;
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.containsKey(key)) {
                value = bundle.getLong(key, -1);
                if (value != -1) {
                    return value;
                }
            }
        }
        return 0;
    }

    /**
     * Get a Bundle from a Bundle
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or null when not found
     */
    public static Bundle getBundleFromBundles(final @Nullable String key, final @NonNull Bundle... bundles) {
        Bundle value;
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.containsKey(key)) {
                value = bundle.getBundle(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Utility routine to get the list from the passed bundles. Added to reduce lint warnings...
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return List, or null when not present
     */
    @Nullable
    public static <T extends Parcelable> ArrayList<T> getParcelableArrayList(final @Nullable String key, final @NonNull Bundle... bundles) {
        ArrayList<T> value;
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.containsKey(key)) {
                value = bundle.getParcelableArrayList(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
}

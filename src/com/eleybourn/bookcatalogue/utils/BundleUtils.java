package com.eleybourn.bookcatalogue.utils;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public final class BundleUtils {

    private BundleUtils() {
    }

    /**
     * Check if passed Bundle contains a non-blank string.
     *
     * @param bundle to check
     * @param key    to check for
     *
     * @return Present/absent
     */
    public static boolean isNonBlankString(@NonNull final Bundle bundle,
                                           @NonNull final String key) {
        String s = bundle.getString(key);
        return (s != null && !s.trim().isEmpty());
    }

    /**
     * Add the value to the Bundle if not present.
     *
     * @param bundle to check
     * @param key    for data to add
     * @param value  to use
     */
    public static void addIfNotPresent(@NonNull final Bundle bundle,
                                       @NonNull final String key,
                                       @NonNull final String value) {
        String test = bundle.getString(key);
        if (test == null || test.isEmpty()) {
            bundle.putString(key, value.trim());
        }
    }

    /**
     * Get a string from a Bundle.
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or null when not found
     */
    public static String getStringFromBundles(@NonNull final String key,
                                              @NonNull final Bundle... bundles) {
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
     * Get a int from a Bundle.
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or 0 when not found
     */
    public static int getIntFromBundles(@NonNull final String key,
                                        @NonNull final Bundle... bundles) {
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
     * Get a long from a Bundle.
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or 0 when not found
     */
    public static long getLongFromBundles(@Nullable final String key,
                                          @NonNull final Bundle... bundles) {
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
     * Get a Bundle from a Bundle.
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return Result or null when not found
     */
    public static Bundle getBundleFromBundles(@Nullable final String key,
                                              @NonNull final Bundle... bundles) {
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
     * Get the list from the passed bundles. Added to reduce lint warnings...
     *
     * @param key     to check for
     * @param bundles to check
     *
     * @return List, or null when not present
     */
    @Nullable
    public static <T extends Parcelable> ArrayList<T> getParcelableArrayList(@Nullable final String key,
                                                                             @NonNull final Bundle... bundles) {
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

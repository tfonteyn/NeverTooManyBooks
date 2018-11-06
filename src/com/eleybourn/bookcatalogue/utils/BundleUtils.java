package com.eleybourn.bookcatalogue.utils;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
     * Get a value from a Bundle and convert to a long.
     *
     * @param bundle to check
     * @param key    to check for
     *
     * @return Result
     *
     * @throws NumberFormatException if it was a string with an invalid format
     */
    public static long getLongFromBundle(final @NonNull Bundle bundle, final @Nullable String key)
            throws NumberFormatException {
        Object value = bundle.get(key);

        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof String) {
            return Long.parseLong((String) value);
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else {
            throw new NumberFormatException("Not a long value: " + value);
        }
    }

//    public static void dumpBundle(final @NonNull Class clazz, final @Nullable Bundle bundle) {
//        if (bundle == null) {
//            Logger.debug("Bundle was null");
//            return;
//        }
//        if (bundle.isEmpty()) {
//            Logger.debug("Bundle was empty");
//            return;
//        }
//
//        for (String key : bundle.keySet()) {
//            Object value = bundle.get(key);
//
//            if (value instanceof String) {
//                Logger.info(clazz,key + "(String)=`" + bundle.getString(key) + "`");
//            } else if (value instanceof Integer) {
//                Logger.info(clazz, key + "(Integer)=`" + bundle.getInt(key) + "`");
//            } else if (value instanceof Long) {
//                Logger.info(clazz, key + "(Long)=`" + bundle.getLong(key) + "`");
//            }  else if (value instanceof Float) {
//                Logger.info(clazz,key + "(Float)=`" + bundle.getFloat(key) + "`");
//            }  else if (value instanceof Boolean) {
//                Logger.info(clazz,key + "(Boolean)=`" + bundle.getBoolean(key) + "`");
//            }  else if (value instanceof Double) {
//                Logger.info(clazz,key + "(Double)=`" + bundle.getDouble(key) + "`");
//            }
//        }
//    }

}

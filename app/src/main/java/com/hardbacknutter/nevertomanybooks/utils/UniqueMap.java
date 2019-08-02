package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * Subclass of HashMap with an add(...) method that ensures values are unique.
 *
 * @param <K> Type of Key values
 * @param <V> Type of data values
 *
 * @author pjw
 */
public class UniqueMap<K, V>
        extends HashMap<K, V> {

    private static final long serialVersionUID = 425468000396955263L;

    /**
     * @param key   Key for new value
     * @param value Data for new value
     */
    @Override
    @NonNull
    @CallSuper
    public V put(@NonNull final K key,
                 @NonNull final V value) {
        if (super.put(key, value) != null) {
            throw new IllegalArgumentException("Map already contains key value: " + key);
        }
        /*
         * collection contract says to return the previous value associated with the key,
         * or {@code null} if there was no mapping for that key.
         */
        return value;
    }
}

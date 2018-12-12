package com.eleybourn.bookcatalogue.utils;

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
public class UniqueMap<K, V> extends HashMap<K, V> {
    private static final long serialVersionUID = 1L;

    /**
     * @param key   Key for new value
     * @param value Data for new value
     *
     * @throws IllegalArgumentException if key already stored
     */
    @Override
    @NonNull
    @CallSuper
    public V put(final @NonNull K key, final @NonNull V value) {
        if (super.put(key, value) != null) {
            throw new IllegalArgumentException("Map already contains key value" + key);
        }
        /*
         * collection contract says to return the previous value associated with <tt>key</tt>,
         * or <tt>null</tt> if there was no mapping for <tt>key</tt>.
         */
        return value;
    }
}

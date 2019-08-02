package com.hardbacknutter.nevertomanybooks.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.datamanager.Datum;

/**
 * A bitmask is read/written to the database as a long.
 * We need it as a boolean.
 * <p>
 * Transform setting/resetting a single bit.
 * A 'get' returns a Boolean
 * A 'set will store it back as a long with the bit set/reset.
 */
public class BitmaskDataAccessor
        implements DataAccessor {

    /** this is the ACTUAL key into the 'rawData' object. */
    private final String mKey;
    /** the bit we're handling in this object. */
    private final long mBitmask;

    /**
     * Constructor.
     *
     * @param key     The key for the actual data
     * @param bitmask to manage
     */
    public BitmaskDataAccessor(@NonNull final String key,
                               final long bitmask) {
        mKey = key;
        mBitmask = bitmask;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final Bundle rawData) {
        return (Datum.toLong(rawData.get(mKey)) & mBitmask) != 0;
    }

    @Override
    public void put(@NonNull final Bundle rawData,
                    @NonNull final Object value) {

        long bits = Datum.toLong(rawData.get(mKey));

        if (Datum.toBoolean(value)) {
            // set the bit
            bits |= mBitmask;
        } else {
            // or reset the bit
            bits &= ~mBitmask;
        }

        rawData.putLong(mKey, bits);
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}

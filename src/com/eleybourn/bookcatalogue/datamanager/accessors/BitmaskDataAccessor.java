package com.eleybourn.bookcatalogue.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.datamanager.Datum;

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

    /** key of the real data object. */
    private final String mKey;
    /** the bit we're handling in this object. */
    private final int mBitmask;

    /**
     * Constructor.
     *
     * @param key     The key for the actual data
     * @param bitmask to manage
     */
    public BitmaskDataAccessor(@NonNull final String key,
                               final int bitmask) {
        mKey = key;
        mBitmask = bitmask;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final Bundle rawData) {
        return (rawData.getLong(mKey) & mBitmask) != 0;
    }

    @Override
    public void put(@NonNull final Bundle rawData,
                    @NonNull final Object value) {

        long bits = rawData.getLong(mKey);

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

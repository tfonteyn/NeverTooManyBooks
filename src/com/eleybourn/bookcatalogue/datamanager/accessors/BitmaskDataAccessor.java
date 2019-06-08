package com.eleybourn.bookcatalogue.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * A bitmask is stored as an int in the database. But the DataManager loads it as a long.
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
    private final int mBit;

    /**
     * Constructor.
     *
     * @param key The key for the actual data
     * @param bit to manage
     */
    public BitmaskDataAccessor(@NonNull final String key,
                               final int bit) {
        mKey = key;
        mBit = bit;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager dataManager,
                       @NonNull final Bundle rawData,
                       @NonNull final Datum datum) {
        return dataManager.isBitSet(mKey, mBit);
    }

    @Override
    public void put(@NonNull final DataManager dataManager,
                    @NonNull final Bundle rawData,
                    @NonNull final Datum datum,
                    @NonNull final Object value) {

        dataManager.setBit(mKey, mBit, Datum.toBoolean(value));
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}

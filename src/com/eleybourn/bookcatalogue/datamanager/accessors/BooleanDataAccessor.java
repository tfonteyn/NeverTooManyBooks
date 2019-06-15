package com.eleybourn.bookcatalogue.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * The database value is stored as an int (0 or 1). Transform to/from boolean
 */
public class BooleanDataAccessor
        implements DataAccessor {

    /** this is the ACTUAL key into the 'rawData' object. */
    private final String mKey;

    /**
     * Constructor.
     *
     * @param key The key for the actual data
     */
    public BooleanDataAccessor(@NonNull final String key) {
        mKey = key;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final Bundle rawData) {
        // The database stores booleans as integers. We also want to deal with non-integer values
        // that somehow represent a boolean.
        return Datum.toBoolean(rawData.get(mKey));
    }

    @Override
    public void put(@NonNull final Bundle rawData,
                    @NonNull final Object value) {
        rawData.putBoolean(mKey, Datum.toBoolean(value));
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}

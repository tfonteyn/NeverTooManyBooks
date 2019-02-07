package com.eleybourn.bookcatalogue.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * The database value is stored as an int (0 or 1). Transform to/from boolean
 */
public class BooleanDataAccessor
        implements DataAccessor {

    /** this is the ACTUAL key into the DataManager object. */
    private final String mKey;

    /**
     * @param key The key for the actual data
     */
    public BooleanDataAccessor(@NonNull final String key) {
        mKey = key;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager dataManager,
                       @NonNull final Bundle rawData,
                       @NonNull final Datum datum) {
        return Datum.toBoolean(rawData.get(mKey));
    }

    @Override
    public void put(@NonNull final DataManager dataManager,
                    @NonNull final Bundle rawData,
                    @NonNull final Datum datum,
                    @NonNull final Object value) {
        rawData.putBoolean(mKey, Datum.toBoolean(value));
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}

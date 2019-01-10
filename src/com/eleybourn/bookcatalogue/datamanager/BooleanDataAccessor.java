package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import androidx.annotation.NonNull;

public class BooleanDataAccessor
        implements DataAccessor {

    private final String mKey;

    public BooleanDataAccessor(@NonNull final String key) {
        this.mKey = key;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager data,
                       @NonNull final Datum datum,
                       @NonNull final Bundle rawData) {
        return data.getInt(mKey) != 0;
    }

    @Override
    public void set(@NonNull final DataManager data,
                    @NonNull final Datum datum,
                    @NonNull final Bundle rawData,
                    @NonNull final Object value) {
        data.putBoolean(mKey, Datum.toBoolean(value));

    }

    @Override
    public boolean isPresent(@NonNull final DataManager data,
                             @NonNull final Datum datum,
                             @NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }

}

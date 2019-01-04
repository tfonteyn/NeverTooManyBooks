package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import androidx.annotation.NonNull;

public class BooleanDataAccessor implements DataAccessor {
    private final String key;

    public BooleanDataAccessor(@NonNull final String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager data,
                       @NonNull final Datum datum,
                       @NonNull final Bundle rawData) {
        return data.getInt(key) != 0;
    }

    @Override
    public void set(@NonNull final DataManager data,
                    @NonNull final Datum datum,
                    @NonNull final Bundle rawData,
                    @NonNull final Object value) {
        data.putBoolean(key, Datum.toBoolean(value));

    }

    @Override
    public boolean isPresent(@NonNull final DataManager data,
                             @NonNull final Datum datum,
                             @NonNull final Bundle rawData) {
        return rawData.containsKey(key);
    }

}

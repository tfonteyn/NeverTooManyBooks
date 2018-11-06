package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import android.support.annotation.NonNull;

public class BooleanDataAccessor implements DataAccessor {
    private final String key;

    public BooleanDataAccessor(final @NonNull String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public Boolean get(final @NonNull DataManager data,
                       final @NonNull Datum datum,
                       final @NonNull Bundle rawData) {
        return data.getInt(key) != 0;
    }

    @Override
    public void set(final @NonNull DataManager data,
                    final @NonNull Datum datum,
                    final @NonNull Bundle rawData,
                    final @NonNull Object value) {
        data.putBoolean(key, Datum.toBoolean(value));

    }

    @Override
    public boolean isPresent(final @NonNull DataManager data,
                             final @NonNull Datum datum,
                             final @NonNull Bundle rawData) {
        return rawData.containsKey(key);
    }

}

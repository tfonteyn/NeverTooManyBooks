package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import androidx.annotation.NonNull;

public class BitmaskDataAccessor implements DataAccessor {
    private final String key;
    private final int bit;

    public BitmaskDataAccessor(@NonNull final String key, final int bit) {
        this.key = key;
        this.bit = bit;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
        Integer bitmask = data.getInt(key);
        return (bitmask & bit) != 0;
    }

    @Override
    public void set(@NonNull final DataManager data,
                    @NonNull final Datum datum,
                    @NonNull final Bundle rawData,
                    @NonNull final Object value) {
        Integer bitmask = data.getInt(key);
        // Parse the string the CheckBox returns us (0 or 1)
        if (Datum.toBoolean(value)) {
            bitmask |= bit;
        } else {
            bitmask &= ~bit;
        }
        data.putInt(key, bitmask);

    }

    @Override
    public boolean isPresent(@NonNull final DataManager data,
                             @NonNull final Datum datum,
                             @NonNull final Bundle rawData) {
        return rawData.containsKey(key);
    }
}

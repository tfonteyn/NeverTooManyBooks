package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import androidx.annotation.NonNull;

public class BitmaskDataAccessor implements DataAccessor {
    private final String key;
    private final int bit;

    public BitmaskDataAccessor(final @NonNull String key, final int bit) {
        this.key = key;
        this.bit = bit;
    }

    @NonNull
    @Override
    public Boolean get(final @NonNull DataManager data, final @NonNull Datum datum, final @NonNull Bundle rawData) {
        Integer bitmask = data.getInt(key);
        return (bitmask & bit) != 0;
    }

    @Override
    public void set(final @NonNull DataManager data,
                    final @NonNull Datum datum,
                    final @NonNull Bundle rawData,
                    final @NonNull Object value) {
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
    public boolean isPresent(final @NonNull DataManager data,
                             final @NonNull Datum datum,
                             final @NonNull Bundle rawData) {
        return rawData.containsKey(key);
    }
}

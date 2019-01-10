package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import androidx.annotation.NonNull;

public class BitmaskDataAccessor
        implements DataAccessor {

    private final String mKey;
    private final int mBit;

    public BitmaskDataAccessor(@NonNull final String key,
                               final int bit) {
        mKey = key;
        mBit = bit;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final DataManager data,
                       @NonNull final Datum datum,
                       @NonNull final Bundle rawData) {
        Integer bitmask = data.getInt(mKey);
        return (bitmask & mBit) != 0;
    }

    @Override
    public void set(@NonNull final DataManager data,
                    @NonNull final Datum datum,
                    @NonNull final Bundle rawData,
                    @NonNull final Object value) {
        Integer bitmask = data.getInt(mKey);
        // Parse the string the CheckBox returns us (0 or 1)
        if (Datum.toBoolean(value)) {
            bitmask |= mBit;
        } else {
            bitmask &= ~mBit;
        }
        data.putInt(mKey, bitmask);

    }

    @Override
    public boolean isPresent(@NonNull final DataManager data,
                             @NonNull final Datum datum,
                             @NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}

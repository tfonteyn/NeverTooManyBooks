package com.eleybourn.bookcatalogue.booklist.prefs;

import com.eleybourn.bookcatalogue.utils.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * An Integer is stored as a String
 *
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 */
public class PInteger
    extends PPrefBase<Integer>
    implements PInt {

    public PInteger(@StringRes final int key,
                    @Nullable final String uuid) {
        super(key, uuid, 0);
    }

    protected PInteger(@StringRes final int key,
                       @Nullable final String uuid,
                       @NonNull final Integer defaultValue) {
        super(key, uuid, defaultValue);
    }

    @NonNull
    @Override
    public Integer get() {
        if (mUuid == null) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // Use a workaround for the real default value not being a String.
            String sValue = Prefs.getPrefs(mUuid).getString(getKey(), null);
            if (sValue == null || sValue.isEmpty()) {
                return mDefaultValue;
            }
            return Integer.parseInt(sValue);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PInteger{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}

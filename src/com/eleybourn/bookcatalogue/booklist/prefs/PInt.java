package com.eleybourn.bookcatalogue.booklist.prefs;

import com.eleybourn.bookcatalogue.utils.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * An Integer is stored as a String
 *
 * Used for {@link androidx.preference.ListPreference}
 * and {@link androidx.preference.MultiSelectListPreference}
 */
public class PInt extends PPrefBase<Integer> {

    public PInt(@StringRes final int key) {
        super(key, 0);
    }

    protected PInt(@StringRes final int key,
                   @NonNull final Integer defaultValue) {
        super(key, defaultValue);
    }

    @NonNull
    @Override
    public Integer get(@Nullable final String uuid) {
        if (uuid == null) {
            return nonPersistedValue != null ? nonPersistedValue : defaultValue;
        } else {
            // Use a workaround for the real default value not being a String.
            String sValue = Prefs.getPrefs(uuid).getString(getKey(), null);
            if (sValue == null || sValue.isEmpty()) {
                return defaultValue;
            }
            return Integer.parseInt(sValue);
        }
    }
}

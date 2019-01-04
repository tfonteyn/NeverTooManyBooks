package com.eleybourn.bookcatalogue.booklist.prefs;

import com.eleybourn.bookcatalogue.utils.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A  String is stored as a String
 *
 * Used for {@link androidx.preference.EditTextPreference}
 */
public class PString extends PPrefBase<String> {

    public PString(@StringRes final int key) {
        super(key, "");
    }

    @NonNull
    @Override
    public String get(@Nullable final String uuid) {
        if (uuid == null) {
            return nonPersistedValue != null ? nonPersistedValue : defaultValue;
        } else {
            return Prefs.getPrefs(uuid).getString(getKey(), defaultValue);
        }
    }
}

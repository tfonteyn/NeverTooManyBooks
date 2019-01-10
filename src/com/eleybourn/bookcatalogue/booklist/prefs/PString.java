package com.eleybourn.bookcatalogue.booklist.prefs;

import com.eleybourn.bookcatalogue.utils.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A String is stored as a String.
 *
 * Used for {@link androidx.preference.EditTextPreference}
 */
public class PString extends PPrefBase<String> {

    public PString(@StringRes final int key,
                   @Nullable final String uuid) {
        super(key, uuid, "");
    }

    @NonNull
    @Override
    public String get() {
        if (uuid == null) {
            return nonPersistedValue != null ? nonPersistedValue : defaultValue;
        } else {
            return Prefs.getPrefs(uuid).getString(getKey(), defaultValue);
        }
    }
}

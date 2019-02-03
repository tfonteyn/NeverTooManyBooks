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
        if (mUuid == null) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // guard against the pref being there, but with value null.
            String tmp = Prefs.getPrefs(mUuid).getString(getKey(), mDefaultValue);
            return tmp != null ? tmp : mDefaultValue;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PString{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}

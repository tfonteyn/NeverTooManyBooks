package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.utils.Prefs;

/**
 * A Boolean is stored as a Boolean.
 *
 * Used for {@link androidx.preference.SwitchPreference}
 */
public class PBoolean
        extends PPrefBase<Boolean> {

    public PBoolean(@StringRes final int key,
                    @Nullable final String uuid) {
        super(key, uuid, false);
    }

    public PBoolean(@StringRes final int key,
                    @Nullable final String uuid,
                    @NonNull final Boolean defaultValue) {
        super(key, uuid, defaultValue);
    }

    @Override
    public void set(@Nullable final Boolean value) {
        if (mUuid == null) {
            mNonPersistedValue = value;
        } else if (value == null) {
            Prefs.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            Prefs.getPrefs(mUuid).edit().putBoolean(getKey(), value).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Boolean value) {
        if (value != null) {
            ed.putBoolean(getKey(), value);
        } else {
            ed.remove(getKey());
        }
    }

    @NonNull
    @Override
    public Boolean get() {
        if (mUuid == null) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            return Prefs.getPrefs(mUuid).getBoolean(getKey(), mDefaultValue);
        }
    }

    /**
     * syntax sugar...
     */
    public boolean isTrue() {
        return get();
    }

    @Override
    @NonNull
    public String toString() {
        return "PBoolean{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}

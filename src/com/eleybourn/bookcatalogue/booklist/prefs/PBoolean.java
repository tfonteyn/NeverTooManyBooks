package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.utils.Prefs;

/**
 * A Boolean is stored as a Boolean.
 * <p>
 * Used for {@link androidx.preference.SwitchPreference}
 */
public class PBoolean
        extends PPrefBase<Boolean> {

    /**
     * Constructor. Uses the global setting as the default value, or false if none.
     *
     * @param key  of the preference
     * @param uuid the style id
     */
    public PBoolean(@StringRes final int key,
                    @NonNull final String uuid) {
        super(key, uuid, Prefs.getBoolean(key, false));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    public PBoolean(@StringRes final int key,
                    @NonNull final String uuid,
                    @NonNull final Boolean defaultValue) {
        super(key, uuid, Prefs.getBoolean(key, defaultValue));
    }

    @Override
    public void set(@Nullable final Boolean value) {
        if (mUuid.isEmpty()) {
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
        if (mUuid.isEmpty()) {
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
    /**
     * syntax sugar...
     */
    public boolean isFalse() {
        return !get();
    }

    @Override
    @NonNull
    public String toString() {
        return "PBoolean{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}

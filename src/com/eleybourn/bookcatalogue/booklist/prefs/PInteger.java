package com.eleybourn.bookcatalogue.booklist.prefs;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.App;

/**
 * An Integer is stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 */
public class PInteger
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value, or 0 if none.
     *
     * @param key  of the preference
     * @param uuid the style id
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent) {
        super(key, uuid, isPersistent, App.getListPreference(key, 0));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final Integer defaultValue) {
        super(key, uuid, isPersistent, App.getListPreference(key, defaultValue));
    }

    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // Use a workaround for the real default value not being a String.
            String value = App.getPrefs(mUuid).getString(getKey(), null);
            if (value == null || value.isEmpty()) {
                return mDefaultValue;
            }
            return Integer.parseInt(value);
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

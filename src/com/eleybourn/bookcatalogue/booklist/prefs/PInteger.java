package com.eleybourn.bookcatalogue.booklist.prefs;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.utils.Prefs;

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
    public PInteger(@StringRes final int key,
                    @NonNull final String uuid) {
        super(key, uuid, Prefs.getListPreference(key, 0));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    protected PInteger(@StringRes final int key,
                       @NonNull final String uuid,
                       @NonNull final Integer defaultValue) {
        super(key, uuid, Prefs.getListPreference(key, defaultValue));
    }

    @NonNull
    @Override
    public Integer get() {
        if (mUuid.isEmpty()) {
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

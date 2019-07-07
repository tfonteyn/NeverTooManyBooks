package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.settings.Prefs;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * We basically want a bitmask/int.
 * But the Preference insists on a {@code Set<String>}
 */
public class PBitmask
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    public PBitmask(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    final int defaultValue) {
        super(key, uuid, isPersistent, App.getMultiSelectListPreference(key, defaultValue));
    }

    /**
     * converts the Integer bitmask and stores it as a {@code Set<String>}
     */
    @Override
    public void set(@Nullable final Integer value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            App.getPrefs(mUuid).edit().putStringSet(getKey(), Prefs.toStringSet(value)).apply();
        }
    }

    /**
     * converts the Integer bitmask and stores it as a {@code Set<String>}.
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Integer value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putStringSet(getKey(), Prefs.toStringSet(value));
        }
    }

    /**
     * Reads a {@code Set<String>} from storage, and converts it to an Integer bitmask.
     */
    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            Set<String> value = App.getPrefs(mUuid).getStringSet(getKey(), null);
            if (value == null || value.isEmpty()) {
                return mDefaultValue;
            }
            return Prefs.toInteger(value);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{" + super.toString()
                + ",value=`" + Prefs.toStringSet(get()) + '`'
                + '}';
    }
}

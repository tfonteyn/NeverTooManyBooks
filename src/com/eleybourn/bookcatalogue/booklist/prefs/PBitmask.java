package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;

import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}
 *
 * We basically want a bitmask/int.
 * But the Preference insists on a Set<String>
 */
public class PBitmask
    extends PPrefBase<Integer>
    implements PInt {

    public PBitmask(@StringRes final int key,
                    @Nullable final String uuid) {
        super(key, uuid, 0x0000);
    }

    /**
     * converts the Integer bitmask and stores it as a Set<String>
     */
    @Override
    public void set(@Nullable final Integer value) {
        if (mUuid == null) {
            mNonPersistedValue = value;
        } else if (value == null) {
            Prefs.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            Prefs.getPrefs(mUuid).edit()
                 .putStringSet(getKey(), toStringSet(value)).apply();
        }
    }

    /**
     * converts the Integer bitmask and stores it as a Set<String>
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final Integer value) {
        if (value != null) {
            ed.putStringSet(getKey(), toStringSet(value));
        } else {
            ed.remove(getKey());
        }
    }

    /**
     * Reads a Set<String> from storage, and converts it to an Integer bitmask.
     */
    @NonNull
    @Override
    public Integer get() {
        if (mUuid == null) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            Set<String> sValue = Prefs.getPrefs(mUuid).getStringSet(getKey(), null);
            if (sValue == null || sValue.isEmpty()) {
                return mDefaultValue;
            }
            return toInteger(sValue);
        }
    }

    @NonNull
    private Integer toInteger(@NonNull final Set<String> sValue) {
        Integer tmp = 0;
        for (String s : sValue) {
            tmp += Integer.parseInt(s);
        }
        return tmp;
    }

    @NonNull
    private Set<String> toStringSet(@NonNull final Integer value) {
        Set<String> set = new HashSet<>();
        Integer tmp = value;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                set.add(String.valueOf(bit));
            }
            bit *= 2;
            tmp = tmp >> 1;
        }
        return set;
    }
}

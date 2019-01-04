package com.eleybourn.bookcatalogue.booklist.prefs;

import android.os.Parcel;

import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * An List<Integer> is stored as a Set<String>
 *
 * No equivalent Preference widget
 */
public class PIntList
    extends PSetBase<Integer, List<Integer>> {

    public PIntList(@StringRes final int key) {
        super(key, new ArrayList<Integer>());
        nonPersistedValue = new ArrayList<>();
    }

    @Override
    public void set(@Nullable final String uuid,
                    @NonNull final Parcel in) {
        List<Integer> tmp = new ArrayList<>();
        in.readList(tmp, getClass().getClassLoader());
        set(uuid, tmp);
    }

    @NonNull
    @Override
    public List<Integer> get(@Nullable final String uuid) {
        if (uuid == null) {
            return nonPersistedValue != null ? nonPersistedValue : defaultValue;
        } else {
            Set<String> sValue = Prefs.getPrefs(uuid).getStringSet(getKey(), null);
            if (sValue == null || sValue.isEmpty()) {
                return defaultValue;
            }
            List<Integer> list = new ArrayList<>();
            for (String s : sValue) {
                list.add(Integer.parseInt(s));
            }
            return list;
        }
    }
}

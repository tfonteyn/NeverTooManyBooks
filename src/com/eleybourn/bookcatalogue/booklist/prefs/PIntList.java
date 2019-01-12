package com.eleybourn.bookcatalogue.booklist.prefs;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * An List<Integer> is stored as a CSV String.
 *
 * No equivalent Preference widget
 */
public class PIntList
        extends PCollectionBase<Integer, List<Integer>> {

    public PIntList(@StringRes final int key,
                    @Nullable final String uuid) {
        super(key, uuid, new ArrayList<Integer>());
        mNonPersistedValue = new ArrayList<>();
    }

    @Override
    public void set(@NonNull final Parcel in) {
        List<Integer> tmp = new ArrayList<>();
        in.readList(tmp, getClass().getClassLoader());
        set(tmp);
    }

    @NonNull
    @Override
    public List<Integer> get() {
        if (mUuid == null) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            String sValues = Prefs.getPrefs(mUuid).getString(getKey(), null);
            if (sValues == null || sValues.isEmpty()) {
                return mDefaultValue;
            }

            return getAsList(sValues);
        }
    }

    @NonNull
    private List<Integer> getAsList(@NonNull final String sValues) {
        List<Integer> list = new ArrayList<>();
        for (String s : sValues.split(DELIM)) {
            list.add(Integer.parseInt(s));
        }
        return list;
    }
}

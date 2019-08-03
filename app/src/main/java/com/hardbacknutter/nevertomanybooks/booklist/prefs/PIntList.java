package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@code List<Integer>} is stored as a CSV String.
 * <p>
 * No equivalent Preference widget
 */
public class PIntList
        extends PCollectionBase<Integer, List<Integer>> {

    /**
     * Constructor.
     *
     * @param key  key of preference
     * @param uuid of the style
     */
    protected PIntList(@NonNull final String key,
                       @NonNull final String uuid,
                       final boolean isPersistent) {
        super(key, uuid, isPersistent, new ArrayList<>());
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
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            String values = getPrefs().getString(getKey(), null);
            if (values == null || values.isEmpty()) {
                return mDefaultValue;
            }

            return getAsList(values);
        }
    }

    @NonNull
    private List<Integer> getAsList(@NonNull final String values) {
        List<Integer> list = new ArrayList<>();
        for (String s : values.split(DELIM)) {
            list.add(Integer.parseInt(s));
        }
        return list;
    }

    @Override
    @NonNull
    public String toString() {
        return "PIntList{" + super.toString() + '}'
                + ",value=`" + get() + '`';
    }
}

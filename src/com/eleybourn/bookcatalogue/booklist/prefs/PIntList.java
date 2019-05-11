package com.eleybourn.bookcatalogue.booklist.prefs;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.App;

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
                       @NonNull final String uuid) {
        super(key, uuid, new ArrayList<>());
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
        if (mUuid.isEmpty()) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            String sValues = App.getPrefs(mUuid).getString(getKey(), null);
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

    @Override
    @NonNull
    public String toString() {
        return "PIntList{" + super.toString() + '}'
                + ",value=`" + get() + '`';
    }
}

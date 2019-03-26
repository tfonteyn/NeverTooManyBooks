package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.eleybourn.bookcatalogue.App;

/**
 * Base for a Collection (List, Set,...) of elements (Integer, String, ...)
 * <p>
 * All of them are written as a CSV String to preserve the order.
 *
 * @param <E> type of collection element
 * @param <T> type of collection, e.f. List,Set
 */
public abstract class PCollectionBase<E, T extends Collection<E>>
        extends PPrefBase<T>
        implements PCollection<E> {

    static final String DELIM = ",";

    /**
     * Constructor.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param defaultValue in memory default
     */
    PCollectionBase(@NonNull final String key,
                    @NonNull final String uuid,
                    @NonNull final T defaultValue) {
        super(key, uuid, defaultValue);
    }

    @Override
    public void set(@Nullable final T value) {
        if (mUuid.isEmpty()) {
            mNonPersistedValue = value;
        } else if (value == null) {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            App.getPrefs(mUuid).edit()
               .putString(getKey(), TextUtils.join(DELIM, value)).apply();
        }
    }

    /**
     * Bypass the real type
     */
    public void set(@Nullable final Set<String> value) {
        if (mUuid.isEmpty()) {
            throw new IllegalArgumentException("uuid was empty");
        }

        if (value == null) {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            App.getPrefs(mUuid).edit()
               .putString(getKey(), TextUtils.join(DELIM, value)).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final T value) {
        if (value != null) {
            ed.putString(getKey(), TextUtils.join(DELIM, value));
        } else {
            ed.remove(getKey());
        }
    }

    public void clear() {
        if (mUuid.isEmpty()) {
            //noinspection ConstantConditions
            mNonPersistedValue.clear();
        } else {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        }
    }

    /**
     * Add a new element to the end of the list
     *
     * @param value to add
     */
    public void add(@NonNull final E value) {
        if (mUuid.isEmpty()) {
            //noinspection ConstantConditions
            mNonPersistedValue.add(value);
        } else {
            String sValues = App.getPrefs(mUuid).getString(getKey(), null);
            if (sValues == null) {
                sValues = String.valueOf(value);
            } else {
                sValues += DELIM + value;
            }
            App.getPrefs(mUuid).edit().putString(getKey(), sValues).apply();
        }
    }

    public void remove(@NonNull final E value) {
        if (mUuid.isEmpty()) {
            //noinspection ConstantConditions
            mNonPersistedValue.remove(value);
        } else {
            String sValues = App.getPrefs(mUuid).getString(getKey(), null);
            if (sValues != null && !sValues.isEmpty()) {
                List<String> newList = new ArrayList<>();
                for (String e : sValues.split(DELIM)) {
                    if (!e.equals(String.valueOf(value))) {
                        newList.add(e);
                    }
                }
                if (!newList.isEmpty()) {
                    App.getPrefs(mUuid).edit()
                       .putString(getKey(), TextUtils.join(DELIM, newList)).apply();
                } else {
                    App.getPrefs(mUuid).edit().remove(getKey()).apply();
                }
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        if (mUuid.isEmpty()) {
            // builtin ? then write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            //noinspection ConstantConditions
            dest.writeList(new ArrayList<>(mNonPersistedValue));
        } else {
            // write the actual value, this could be the default if we have no value, but that
            // is ok anyhow.
            dest.writeList(new ArrayList<>(get()));
        }
    }
}

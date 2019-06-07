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
                    final boolean isPersistent,
                    @NonNull final T defaultValue) {
        super(key, uuid, isPersistent, defaultValue);
    }


    @Override
    public void set(@Nullable final T value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            App.getPrefs(mUuid).edit().putString(getKey(), TextUtils.join(DELIM, value)).apply();
        }
    }

    /**
     * Bypass the real type
     */
    public void set(@Nullable final Set<String> value) {
        if (!mIsPersistent) {
            throw new IllegalArgumentException("uuid was empty");
        }

        if (value == null) {
            remove();
        } else {
            App.getPrefs(mUuid).edit().putString(getKey(), TextUtils.join(DELIM, value)).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final T value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putString(getKey(), TextUtils.join(DELIM, value));
        }
    }

    public void clear() {
        if (!mIsPersistent) {
            //noinspection ConstantConditions
            mNonPersistedValue.clear();
        } else {
            remove();
        }
    }

    /**
     * Add a new element to the end of the list
     *
     * @param element to add
     */
    public void add(@NonNull final E element) {
        if (!mIsPersistent) {
            //noinspection ConstantConditions
            mNonPersistedValue.add(element);
        } else {
            String value = App.getPrefs(mUuid).getString(getKey(), null);
            if (value == null) {
                value = String.valueOf(element);
            } else {
                value += DELIM + element;
            }
            App.getPrefs(mUuid).edit().putString(getKey(), value).apply();
        }
    }

    public void remove(@NonNull final E element) {
        if (!mIsPersistent) {
            //noinspection ConstantConditions
            mNonPersistedValue.remove(element);
        } else {
            String values = App.getPrefs(mUuid).getString(getKey(), null);
            if (values != null && !values.isEmpty()) {
                List<String> newList = new ArrayList<>();
                for (String e : values.split(DELIM)) {
                    if (!e.equals(String.valueOf(element))) {
                        newList.add(e);
                    }
                }
                if (newList.isEmpty()) {
                    remove();
                } else {
                    App.getPrefs(mUuid).edit().putString(getKey(), TextUtils.join(DELIM, newList)).apply();
                }
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        if (!mIsPersistent) {
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

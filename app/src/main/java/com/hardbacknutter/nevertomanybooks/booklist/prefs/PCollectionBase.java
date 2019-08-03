package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
            getPrefs().edit().putString(getKey(), TextUtils.join(DELIM, value)).apply();
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

    /**
     * Bypass the real type.
     */
    public void set(@Nullable final Set<String> value) {
        if (!mIsPersistent) {
            throw new IllegalArgumentException("uuid was empty");
        }

        if (value == null) {
            remove();
        } else {
            getPrefs().edit().putString(getKey(), TextUtils.join(DELIM, value)).apply();
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
     * Add a new element to the end of the list. The updated list is stored immediately.
     *
     * @param element Element to add
     */
    public void add(@NonNull final E element) {
        if (!mIsPersistent) {
            //noinspection ConstantConditions
            mNonPersistedValue.add(element);
        } else {
            SharedPreferences.Editor ed = getPrefs().edit();
            add(ed, getPrefs().getString(getKey(), null), element);
            ed.apply();
        }
    }

    /**
     * Add a new element to the end of the list.
     *
     * @param list    current list to add the element to, can be {@code null} or empty.
     * @param element Element to add
     *
     * @return updated list string
     */
    @NonNull
    public String add(@NonNull final SharedPreferences.Editor ed,
                      @Nullable String list,
                      @NonNull final E element) {

        if (list == null) {
            list = String.valueOf(element);
        } else {
            list += DELIM + element;
        }
        ed.putString(getKey(), list);

        return list;
    }

    /**
     * Remove an element from the list. The updated list is stored immediately.
     *
     * @param element to remove
     */
    public void remove(@NonNull final E element) {
        if (!mIsPersistent) {
            //noinspection ConstantConditions
            mNonPersistedValue.remove(element);
        } else {
            String list = getPrefs().getString(getKey(), null);
            if (list != null && !list.isEmpty()) {
                List<String> newList = new ArrayList<>();
                for (String e : list.split(DELIM)) {
                    if (!e.equals(String.valueOf(element))) {
                        newList.add(e);
                    }
                }
                if (newList.isEmpty()) {
                    remove();
                } else {
                    getPrefs().edit()
                       .putString(getKey(), TextUtils.join(DELIM, newList))
                       .apply();
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

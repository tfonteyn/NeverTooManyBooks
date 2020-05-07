/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Base for a Collection (List, Set,...) of elements (Integer, String, ...)
 * <p>
 * A Set or a List is always represented by a {@code Set<String>} in the SharedPreferences
 * due to limitations of {@link androidx.preference.ListPreference}
 * and {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * All of them are written as a CSV String to preserve the order.
 *
 * @param <E> type of collection element
 * @param <T> type of collection, e.f. List,Set
 *
 * @see PCsvString
 */
public abstract class PCollectionBase<E, T extends Collection<E>>
        extends PPrefBase<T>
        implements PCsvString {

    public static final String DELIM = ",";

    /**
     * Constructor.
     *
     * @param key          key of preference
     * @param uuid         UUID of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    PCollectionBase(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final T defValue) {
        super(key, uuid, isPersistent, defValue);
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final T value) {
        ed.putString(getKey(), TextUtils.join(DELIM, value));
    }

    @Override
    public void set(@NonNull final Context context,
                    @Nullable final String values) {
        if (mIsPersistent) {
            SharedPreferences.Editor ed = getPrefs(context).edit();
            if (values == null) {
                ed.remove(getKey());
            } else {
                ed.putString(getKey(), values);
            }
            ed.apply();
        } else {
            // Not implemented for now, and in fact not needed/used for now (2020-03-11)
            // Problem is that we'd need to split the incoming CSV string, and re-create the list.
            // But on this level, we don't know the real type of the elements in the Csv string.
            // i.o.w. this needs to be implemented in a concrete class.
            // Aside of that, current usage is that a List is concatenated to a Csv String and
            // given to this method. Implementing the non-persistent branch would bring a
            // pointless double conversion.
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void set(@Nullable final T value) {
        if (mIsPersistent) {
            SharedPreferences.Editor ed = getPrefs(App.getAppContext()).edit();
            if (value == null) {
                ed.remove(getKey());
            } else {
                ed.putString(getKey(), TextUtils.join(DELIM, value));
            }
            ed.apply();
        } else {
            Objects.requireNonNull(mNonPersistedValue, ErrorMsg.NULL_NON_PERSISTED_VALUE);
            mNonPersistedValue.clear();
            if (value != null) {
                mNonPersistedValue.addAll(value);
            }
        }
    }

    public void clear(@NonNull final Context context) {
        if (mIsPersistent) {
            getPrefs(context).edit().remove(getKey()).apply();
        } else {
            Objects.requireNonNull(mNonPersistedValue, ErrorMsg.NULL_NON_PERSISTED_VALUE);
            mNonPersistedValue.clear();
        }
    }

    /**
     * Add a new element to the end of the list. The updated list is stored immediately.
     *
     * @param context Current context
     * @param element Element to add
     */
    public void add(@NonNull final Context context,
                    @NonNull final E element) {
        if (mIsPersistent) {
            SharedPreferences.Editor ed = getPrefs(context).edit();
            add(ed, getPrefs(context).getString(getKey(), null), element);
            ed.apply();
        } else {
            Objects.requireNonNull(mNonPersistedValue, ErrorMsg.NULL_NON_PERSISTED_VALUE);
            mNonPersistedValue.add(element);
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
                      @Nullable final String list,
                      @NonNull final E element) {

        StringBuilder result = new StringBuilder();
        if (list == null) {
            result.append(element);
        } else {
            result.append(list).append(DELIM).append(element);
        }
        ed.putString(getKey(), result.toString());
        return result.toString();
    }

    /**
     * Remove an element from the list. The updated list is stored immediately.
     *
     * @param context Current context
     * @param element to remove
     */
    public void remove(@NonNull final Context context,
                       @NonNull final E element) {
        if (mIsPersistent) {
            String list = getPrefs(context).getString(getKey(), null);
            if (list != null && !list.isEmpty()) {
                Collection<String> newList = new ArrayList<>();
                for (String e : list.split(DELIM)) {
                    if (!e.equals(String.valueOf(element))) {
                        newList.add(e);
                    }
                }
                if (newList.isEmpty()) {
                    getPrefs(context).edit().remove(getKey()).apply();
                } else {
                    getPrefs(context)
                            .edit().putString(getKey(), TextUtils.join(DELIM, newList)).apply();
                }
            }
        } else {
            Objects.requireNonNull(mNonPersistedValue, ErrorMsg.NULL_NON_PERSISTED_VALUE);
            mNonPersistedValue.remove(element);
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        if (mIsPersistent) {
            // write the actual value, this could be the default if we have no value, but that
            // is ok anyhow.
            dest.writeList(new ArrayList<>(getValue(App.getAppContext())));
        } else {
            Objects.requireNonNull(mNonPersistedValue, ErrorMsg.NULL_NON_PERSISTED_VALUE);
            // builtin ? write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeList(new ArrayList<>(mNonPersistedValue));
        }
    }
}

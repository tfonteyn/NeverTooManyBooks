package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;

import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base for a Collection (List, Set,...) of elements (Integer, String, ...)
 *
 * @param <E> type of collection element
 * @param <T> type of collection, e.f. List,Set
 */
public abstract class PSetBase<E, T extends Collection<E>>
    extends PPrefBase<T>
    implements PSet {

    /**
     * @param key          key of preference
     * @param defaultValue in memory default
     */
    PSetBase(final int key,
             @NonNull final T defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public void set(@Nullable final String uuid,
                    @Nullable final T value) {
        if (uuid == null) {
            nonPersistedValue = value;
        } else if (value == null) {
            Prefs.getPrefs(uuid).edit().remove(getKey()).apply();
        } else {
            Prefs.getPrefs(uuid).edit().putStringSet(getKey(), toStringSet(value)).apply();
        }
    }

    /**
     * Bypass the real type
     */
    public void set(@NonNull final String uuid,
                    @NonNull final Set<String> value) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid was null");
        }

        if (value == null) {
            Prefs.getPrefs(uuid).edit().remove(getKey()).apply();
        } else {
            Prefs.getPrefs(uuid).edit().putStringSet(getKey(), value).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final T value) {
        if (value != null) {
            ed.putStringSet(getKey(), toStringSet(value));
        } else {
            ed.remove(getKey());
        }
    }

    public void clear(@Nullable final String uuid) {
        if (uuid == null) {
            nonPersistedValue.clear();
        } else {
            Set<String> sValue = Prefs.getPrefs(uuid).getStringSet(getKey(), null);
            if (sValue == null) {
                return;
            }
            sValue.clear();
            Prefs.getPrefs(uuid).edit().putStringSet(getKey(), sValue).apply();
        }
    }

    public void add(@Nullable final String uuid,
                    @NonNull final E value) {
        if (uuid == null) {
            nonPersistedValue.add(value);
        } else {
            Set<String> sValue = Prefs.getPrefs(uuid).getStringSet(getKey(), null);
            if (sValue == null) {
                sValue = new HashSet<>();
            }
            sValue.add(String.valueOf(value));
            Prefs.getPrefs(uuid).edit().putStringSet(getKey(), sValue).apply();
        }
    }

    public void remove(@Nullable final String uuid,
                       @NonNull final E value) {
        if (uuid == null) {
            nonPersistedValue.remove(value);
        } else {
            Set<String> sValue = Prefs.getPrefs(uuid).getStringSet(getKey(), null);
            if (sValue == null) {
                return;
            }
            sValue.remove(value.toString());
            Prefs.getPrefs(uuid).edit().putStringSet(getKey(), sValue).apply();
        }
    }

    @Override
    public void writeToParcel(@Nullable final String uuid,
                              @NonNull final Parcel dest) {
        if (uuid == null) {
            // builtin ? then write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeList(new ArrayList<>(nonPersistedValue));
        } else {
            // write the actual value, this could be the default if we have no value, but that
            // is what we want for user-defined styles anyhow.
            dest.writeList(new ArrayList<>(get(uuid)));
        }
    }

    @NonNull
    private LinkedHashSet<String> toStringSet(@NonNull final T value) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (E element : value) {
            set.add(String.valueOf(element));
        }
        return set;
    }
}

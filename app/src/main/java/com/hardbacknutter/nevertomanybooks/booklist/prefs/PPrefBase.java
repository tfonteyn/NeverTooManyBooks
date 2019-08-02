package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * Base class for a generic Preference.
 * <p>
 * Note that the methods that take a SharedPreferences.Editor do not need to check
 * on uuid or persistence as obviously that is decided by the caller.
 *
 * @param <T> type of the value to store
 */
public abstract class PPrefBase<T>
        implements PPref<T> {

    /**
     * Copy of the style uuid this Preference belongs to.
     * Convenience only and not locally preserved.
     * Must be set in the constructor.
     */
    @NonNull
    protected final String mUuid;
    /** Flag to indicate the value is persisted. */
    protected final boolean mIsPersistent;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    final T mDefaultValue;
    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in memory value used for non-persistence situations. */
    @Nullable
    T mNonPersistedValue;

    /**
     * Constructor.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param defaultValue in memory default
     */
    PPrefBase(@NonNull final String key,
              @NonNull final String uuid,
              final boolean isPersistent,
              @NonNull final T defaultValue) {
        mKey = key;
        mUuid = uuid;
        mIsPersistent = isPersistent;
        mDefaultValue = defaultValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void remove() {
        if (mIsPersistent) {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        }
    }

    /**
     * for single pref updated.
     * <p>
     * Stores the value as a String
     */
    @Override
    public void set(@Nullable final T value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            App.getPrefs(mUuid).edit().putString(getKey(), String.valueOf(value)).apply();
        }
    }

    /**
     * for batch updates. Can also be used for setting globals.
     * <p>
     * Stores the value as a String by default. Override if you need another type.
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final T value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putString(getKey(), value.toString());
        }
    }

    @Override
    public void set(@NonNull final Parcel in) {
        //noinspection unchecked
        T tmp = (T) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        if (mIsPersistent) {
            // write the actual value, this could be the default if we have no value, but that
            // is what we want for user-defined styles anyhow.
            dest.writeValue(get());
        } else {
            // builtin ? then write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeValue(mNonPersistedValue);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "mKey=" + mKey
                + ", type=" + mDefaultValue.getClass().getSimpleName()
                + ", defaultValue=`" + mDefaultValue + '`'
                + ", mNonPersistedValue=`" + mNonPersistedValue + '`';
    }
}

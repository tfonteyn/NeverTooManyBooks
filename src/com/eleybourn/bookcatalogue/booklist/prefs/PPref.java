package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * None of the PPrefs implement Parcelable but we *do* parcel the 'value'.
 * This does mean the class must be properly constructed with all other fields initialised,
 * before un-parceling the actual value.
 *
 * @param <T> type of the actual value we store.
 */
public interface PPref<T> {

    /**
     * for single updates
     */
    void set(@Nullable final String uuid, @Nullable final T value);

    /**
     * for batch updates
     */
    void set(@NonNull final SharedPreferences.Editor ed, @NonNull final T value);

    void set(@Nullable final String uuid, @NonNull final Parcel in);
    void writeToParcel(@Nullable final String uuid, @NonNull final Parcel dest);

    @NonNull
    T get(@Nullable final String uuid);

    @NonNull
    String getKey();

    void remove(@Nullable final String uuid);
}

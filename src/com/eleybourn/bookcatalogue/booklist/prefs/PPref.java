package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * None of the Preferences implement Parcelable but we *do* parcel the 'value'.
 * This does mean the class must be properly constructed with all other fields initialised,
 * before un-parceling the actual value.
 *
 * @param <T> type of the actual value we store.
 */
public interface PPref<T> {

    /**
     * for single updates.
     */
    void set(@Nullable final T value);

    /**
     * for batch updates.
     */
    void set(@NonNull final SharedPreferences.Editor ed,
             @NonNull final T value);

    void set(@NonNull final Parcel in);

    void writeToParcel(@NonNull final Parcel dest);

    @NonNull
    T get();

    @NonNull
    String getKey();

    void remove();
}

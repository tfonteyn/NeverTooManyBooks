package com.hardbacknutter.nevertomanybooks.booklist.prefs;

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
    void set(@Nullable T value);

    /**
     * for batch updates. Can also be used for setting globals.
     */
    void set(@NonNull SharedPreferences.Editor ed,
             @NonNull T value);

    void set(@NonNull Parcel in);

    void writeToParcel(@NonNull Parcel dest);

    @NonNull
    T get();

    @NonNull
    String getKey();

    void remove();
}

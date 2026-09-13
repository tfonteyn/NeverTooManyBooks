/*
 * @Copyright 2018-2026 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
public class SettingsInput {

    private static final String TAG = "SettingsInput";
    private static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":st";
    private static final String BKEY_AUTO_SCROLL_FLASH = TAG + ":stf";
    private static final String BKEY_MISSING_STORAGE_VOLUME = TAG + ":msv";

    @Nullable
    private String autoScrollKey;
    @Nullable
    private Boolean autoScrollFlash;
    @Nullable
    private Boolean storageVolumeMissing;

    @NonNull
    static SettingsInput fromBundle(@NonNull final Bundle args) {
        return new SettingsInput()
                .setAutoScrollKey(args.getString(BKEY_AUTO_SCROLL_TO_KEY),
                                  args.getBoolean(BKEY_AUTO_SCROLL_FLASH))
                .setStorageVolumeMissing(args.getBoolean(BKEY_MISSING_STORAGE_VOLUME));
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        if (autoScrollKey != null) {
            args.putString(BKEY_AUTO_SCROLL_TO_KEY, autoScrollKey);
        }
        if (autoScrollFlash != null) {
            args.putBoolean(BKEY_AUTO_SCROLL_FLASH, autoScrollFlash);
        }
        if (storageVolumeMissing != null) {
            args.putBoolean(BKEY_MISSING_STORAGE_VOLUME, storageVolumeMissing);
        }
        return args;
    }

    @Nullable
    String getAutoScrollKey() {
        return autoScrollKey;
    }

    /**
     * Auto-scrolling on opening the preference screen to the given key.
     * Optionally 'flash' the option to attract the users attention.
     *
     * @param autoScrollKey   key
     * @param autoScrollFlash flag
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public SettingsInput setAutoScrollKey(@Nullable final String autoScrollKey,
                                          @Nullable final Boolean autoScrollFlash) {
        this.autoScrollKey = autoScrollKey;
        this.autoScrollFlash = autoScrollFlash;
        return this;
    }

    public boolean isAutoScrollFlash() {
        return autoScrollFlash != null && autoScrollFlash;
    }

    boolean isStorageVolumeMissing() {
        return storageVolumeMissing != null && storageVolumeMissing;
    }

    /**
     * Passed in by the startup routines, indicating the storage device was not found.
     *
     * @param storageVolumeMissing flag
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public SettingsInput setStorageVolumeMissing(@Nullable final Boolean storageVolumeMissing) {
        this.storageVolumeMissing = storageVolumeMissing;
        return this;
    }

    @Override
    @NonNull
    public String toString() {
        return "SettingsInput{"
               + "autoScrollKey='" + autoScrollKey + '\''
               + "autoScrollFlash=" + autoScrollFlash
               + ", storageVolumeMissing=" + storageVolumeMissing
               + '}';
    }
}

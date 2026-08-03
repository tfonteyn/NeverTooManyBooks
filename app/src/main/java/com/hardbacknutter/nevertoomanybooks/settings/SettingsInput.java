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

public class SettingsInput {

    private static final String TAG = "SettingsInput";
    private static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":st";
    private static final String BKEY_MISSING_STORAGE_VOLUME = TAG + ":msv";

    @Nullable
    private String autoScrollKey;

    @Nullable
    private final Boolean storageVolumeMissing;

    /**
     * Constructor.
     *
     * @param autoScrollKey        (optional) Allows auto-scrolling on opening
     *                             the preference screen to the given key.
     * @param storageVolumeMissing (optional) Passed in by the startup routines,
     *                             indicating the storage device was not found.
     */
    public SettingsInput(@Nullable final String autoScrollKey,
                         @Nullable final Boolean storageVolumeMissing) {
        this.autoScrollKey = autoScrollKey;
        this.storageVolumeMissing = storageVolumeMissing;
    }

    @NonNull
    static SettingsInput fromBundle(@NonNull final Bundle args) {
        final String autoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        @Nullable
        final Boolean storageVolumeMissing;
        if (args.containsKey(BKEY_MISSING_STORAGE_VOLUME)) {
            storageVolumeMissing = args.getBoolean(BKEY_MISSING_STORAGE_VOLUME);
        } else {
            storageVolumeMissing = null;
        }

        return new SettingsInput(autoScrollToKey, storageVolumeMissing);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        if (autoScrollKey != null) {
            args.putString(BKEY_AUTO_SCROLL_TO_KEY, autoScrollKey);
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

    void setAutoScrollKey(@Nullable final String autoScrollKey) {
        this.autoScrollKey = autoScrollKey;
    }

    boolean isStorageVolumeMissing() {
        return storageVolumeMissing != null && storageVolumeMissing;
    }

    @Override
    @NonNull
    public String toString() {
        return "SettingsInput{"
               + "autoScrollKey='" + autoScrollKey + '\''
               + ", storageVolumeMissing=" + storageVolumeMissing
               + '}';
    }
}

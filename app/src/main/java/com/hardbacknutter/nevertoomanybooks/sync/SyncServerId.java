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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreSyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoSyncServer;

/**
 * The equivalent of an {@code EngineId} for {@link SyncServer} implementations.
 */
public enum SyncServerId
        implements Parcelable {

    /** A Calibre Content Server. */
    Calibre() {
        public boolean isEnabled() {
            return CalibreHandler.isSyncEnabled();
        }

        @NonNull
        @Override
        SyncServer create() {
            return new CalibreSyncServer();
        }
    },

    /** StripInfo website. */
    StripInfo() {
        public boolean isEnabled() {
            return StripInfoHandler.isSyncEnabled();
        }

        @NonNull
        @Override
        SyncServer create() {
            return new StripInfoSyncServer();
        }
    };

    /** {@link Parcelable}. */
    public static final Creator<SyncServerId> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncServerId createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public SyncServerId[] newArray(final int size) {
            return new SyncServerId[size];
        }
    };

    /** Prefixed by the SyncServer preference key, and suffixed by the field name. */
    public static final String FIELDS_UPDATE = ".fields.update.";

    /**
     * Check if this server is globally enabled.
     *
     * @return flag
     */
    public abstract boolean isEnabled();

    /**
     * Create the specific instance.
     *
     * @return new instance
     */
    @NonNull
    abstract SyncServer create();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }
}

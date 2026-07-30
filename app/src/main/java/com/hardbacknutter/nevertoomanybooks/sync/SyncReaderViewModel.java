/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;

public class SyncReaderViewModel
        extends DataReaderViewModel<SyncReaderMetaData, ReaderResults> {

    private static final String ERROR_SYNC_READER_HELPER = "syncReaderHelper";
    @Nullable
    private SyncReaderHelper syncReaderHelper;

    /**
     * Pseudo constructor.
     *
     * @param context Current Context
     * @param args    all arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final SyncServer.Input args) {
        if (syncReaderHelper == null) {
            syncReaderHelper = new SyncReaderHelper(context, args.getSyncServer());
        }
    }

    @NonNull
    @Override
    public String getSourceDisplayName(@NonNull final Context context) {
        return getDataReaderHelper().getSyncServer().getLabel(context);
    }

    /**
     * Get the location to read from.
     *
     * @return the sync-server to use
     */
    @NonNull
    SyncServer getSyncServer() {
        return getDataReaderHelper().getSyncServer();
    }

    /**
     * Get the optional sync-date (cut-off) for use with {@link DataReader.Updates#OnlyNewer}.
     *
     * @return date or {@code null}
     */
    @Nullable
    LocalDateTime getSyncDate() {
        return getDataReaderHelper().getSyncDate();
    }

    /**
     * If we want new-books-only {@link DataReader.Updates#Skip}
     * or new-books-and-updates {@link DataReader.Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     *
     * @param syncDate date
     */
    void setSyncDate(@Nullable final LocalDateTime syncDate) {
        getDataReaderHelper().setSyncDate(syncDate);
    }

    @NonNull
    Bundle getExtraArgs() {
        return getDataReaderHelper().getExtraArgs();
    }

    @Override
    @NonNull
    protected SyncReaderHelper getDataReaderHelper() {
        return Objects.requireNonNull(syncReaderHelper, ERROR_SYNC_READER_HELPER);
    }

    @Override
    public boolean isReadyToGo() {
        Objects.requireNonNull(syncReaderHelper, ERROR_SYNC_READER_HELPER);
        return syncReaderHelper.isReadyToGo();
    }
}

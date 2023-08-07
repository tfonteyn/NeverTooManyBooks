/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

public class SyncReaderViewModel
        extends DataReaderViewModel<SyncReaderMetaData, ReaderResults> {

    private static final String ERROR_SYNC_READER_HELPER = "syncReaderHelper";
    @Nullable
    private SyncReaderHelper syncReaderHelper;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        if (syncReaderHelper == null) {
            final SyncServer syncServer = Objects.requireNonNull(
                    args.getParcelable(SyncServer.BKEY_SITE), SyncServer.BKEY_SITE);

            final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
            syncReaderHelper = new SyncReaderHelper(syncServer, systemLocale);
        }
    }

    @NonNull
    @Override
    public String getSourceDisplayName(@NonNull final Context context) {
        return context.getString(getDataReaderHelper().getSyncServer().getLabelResId());
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

        switch (syncReaderHelper.getSyncServer()) {
            case CalibreCS: {
                @Nullable
                final CalibreLibrary selected = syncReaderHelper
                        .getExtraArgs().getParcelable(CalibreContentServer.BKEY_LIBRARY);
                return selected != null && selected.getTotalBooks() > 0;
            }
            case StripInfo:
                return true;

            default:
                throw new IllegalArgumentException();
        }
    }
}

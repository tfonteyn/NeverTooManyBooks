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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

public class SyncReaderViewModel
        extends DataReaderViewModel<SyncReaderMetaData, ReaderResults> {

    @Nullable
    private SyncReaderHelper syncReaderHelper;

    /**
     * Pseudo constructor.
     */
    public void init(@NonNull final Bundle args) {
        if (syncReaderHelper == null) {
            final SyncServer syncServer = Objects.requireNonNull(
                    args.getParcelable(SyncServer.BKEY_SITE), SyncServer.BKEY_SITE);

            final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
            final DateParser dateParser = new ISODateParser(systemLocale);
            syncReaderHelper = new SyncReaderHelper(syncServer, dateParser);
        }
    }

    @Override
    @NonNull
    public SyncReaderHelper getDataReaderHelper() {
        return Objects.requireNonNull(syncReaderHelper, "syncReaderHelper");
    }

    @Override
    public boolean isReadyToGo() {
        Objects.requireNonNull(syncReaderHelper, "syncReaderHelper");

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

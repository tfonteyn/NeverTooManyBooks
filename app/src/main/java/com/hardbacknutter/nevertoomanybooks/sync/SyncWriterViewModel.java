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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterViewModel;

public class SyncWriterViewModel
        extends DataWriterViewModel<SyncWriterResults> {

    private SyncWriterHelper syncWriterHelper;

    /** UI helper. */
    private boolean quickOptionsAlreadyShown;

    boolean isQuickOptionsAlreadyShown() {
        return quickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown() {
        quickOptionsAlreadyShown = true;
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (syncWriterHelper == null) {
            final SyncServer syncServer = Objects.requireNonNull(
                    args.getParcelable(SyncServer.BKEY_SITE), SyncServer.BKEY_SITE);

            final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
            final DateParser dateParser = new ISODateParser(systemLocale);
            syncWriterHelper = new SyncWriterHelper(syncServer, dateParser);
        }
    }

    @NonNull
    SyncWriterHelper getSyncWriterHelper() {
        return syncWriterHelper;
    }

    @Override
    public boolean isReadyToGo() {
        // slightly bogus test... right now Prefs/Styles are always included,
        // but we're keeping all variations of DataReader/DataWriter classes the same
        return syncWriterHelper.getRecordTypes().size() > 1;
    }

    void startExport() {
        Objects.requireNonNull(syncWriterHelper, "helper");
        startWritingData(syncWriterHelper);
    }
}

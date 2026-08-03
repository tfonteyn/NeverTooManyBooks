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

import androidx.annotation.NonNull;

import java.util.Objects;

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
     * @param args    all arguments
     */
    public void init(@NonNull final SyncServerInput args) {
        if (syncWriterHelper == null) {
            syncWriterHelper = new SyncWriterHelper(args.getSyncServer());
        }
    }

    @NonNull
    protected SyncWriterHelper getDataWriterHelper() {
        return syncWriterHelper;
    }

    void setDeleteLocalBooks(final boolean deleteLocalBooks) {
        getDataWriterHelper().setDeleteLocalBooks(deleteLocalBooks);
    }

    @NonNull
    @Override
    public String getDestinationDisplayName(@NonNull final Context context) {
        return getDataWriterHelper().getSyncServer().getLabel(context);
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

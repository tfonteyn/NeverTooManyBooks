/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.util.EnumSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;

public final class SyncReaderConfig {

    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mImportEntries = EnumSet.noneOf(RecordType.class);

    /** Extra arguments for specific readers. The reader must define them. */
    private final Bundle mExtraArgs = new Bundle();

    /** <strong>How</strong> to handle individual fields. Can be {@code null}. aka unused. */
    @Nullable
    private SyncReaderProcessor mSyncProcessor;

    /**
     * New Books/Covers are always imported (depending on {@link #mImportEntries}).
     * Updated Books/Covers will be handled according to this setting.
     */
    private Updates mUpdateOption;

    /**
     * Constructor.
     */
    SyncReaderConfig(@NonNull final Updates defaultUpdateOption) {
        mUpdateOption = defaultUpdateOption;

        mImportEntries.add(RecordType.MetaData);
        mImportEntries.add(RecordType.Books);
        mImportEntries.add(RecordType.Cover);
    }

    public void setImportEntry(@NonNull final RecordType recordType,
                               final boolean isSet) {
        if (isSet) {
            mImportEntries.add(recordType);
        } else {
            mImportEntries.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getImportEntries() {
        return mImportEntries;
    }

    @Nullable
    public SyncReaderProcessor getSyncProcessor() {
        return mSyncProcessor;
    }

    public void setSyncProcessor(@Nullable final SyncReaderProcessor syncProcessor) {
        mSyncProcessor = syncProcessor;
    }

    @NonNull
    public Bundle getExtraArgs() {
        return mExtraArgs;
    }

    /**
     * Get the {@link Updates} setting.
     *
     * @return setting
     */
    public Updates getUpdateOption() {
        return mUpdateOption;
    }

    public void setUpdateOption(@NonNull final Updates updateOption) {
        mUpdateOption = updateOption;
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderConfig{"
               + "mImportEntries=" + mImportEntries
               + ", mUpdates=" + mUpdateOption
               + ", mExtraArgs=" + mExtraArgs
               + ", mSyncProcessor=" + mSyncProcessor
               + '}';
    }

    /**
     * Existing Books/Covers handling.
     */
    public enum Updates {
        /** skip updates entirely. Current data is untouched. (i.e. new-books only). */
        Skip,
        /** Overwrite current data with incoming data. */
        Overwrite,
        /** check the "update_date" field and only import newer data. */
        OnlyNewer
    }
}

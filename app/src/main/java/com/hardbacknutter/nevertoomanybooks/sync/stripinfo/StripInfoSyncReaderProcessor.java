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

package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;

public class StripInfoSyncReaderProcessor
        extends SyncReaderProcessor {

    public StripInfoSyncReaderProcessor(@NonNull final Context context,
                                        @NonNull final Builder builder) {
        super(context, builder);
    }

    @NonNull
    @Override
    protected FilterResult filter(@NonNull final SyncField field,
                                  @NonNull final Book localBook) {
        if (StripInfoCollectionData.BKEY.equals(field.getKey())) {
            return FilterResult.Add;
        } else {
            return FilterResult.ApplyDefault;
        }
    }

    @Override
    protected boolean process(@NonNull final Context context,
                              @NonNull final Book localBook,
                              @NonNull final Book remoteBook,
                              @NonNull final SyncField field) {
        // When present, we always/effectively do a SyncAction.Overwrite
        return StripInfoCollectionData.BKEY.equals(field.getKey());
    }
}

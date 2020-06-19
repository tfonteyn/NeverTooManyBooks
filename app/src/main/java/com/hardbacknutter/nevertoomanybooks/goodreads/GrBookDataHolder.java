/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class GrBookDataHolder {

    final long id;
    final String isbnStr;

    final long grId;
    final boolean read;
    @Nullable
    final String readStartDate;
    @Nullable
    final String readEndDate;
    @IntRange(from = 0, to = 5)
    final int rating;

    public GrBookDataHolder(@NonNull final DataHolder bookData) {
        id = bookData.getLong(DBDefinitions.KEY_PK_ID);
        isbnStr = bookData.getString(DBDefinitions.KEY_ISBN);

        grId = bookData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
        read = bookData.getBoolean(DBDefinitions.KEY_READ);
        readStartDate = bookData.getString(DBDefinitions.KEY_READ_START);
        readEndDate = bookData.getString(DBDefinitions.KEY_READ_END);
        rating = (int) bookData.getDouble(DBDefinitions.KEY_RATING);
    }
}

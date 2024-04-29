/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

@SuppressWarnings("WeakerAccess")
public class ReadingProgressViewModel
        extends ViewModel {

    private ReadingProgress readingProgress;

    void init(@NonNull final Bundle args) {
        if (readingProgress == null) {
            readingProgress = Objects.requireNonNull(args.getParcelable(DBKey.READ_PROGRESS));
        }
    }

    @NonNull
    ReadingProgress getReadingProgress() {
        return readingProgress;
    }
}

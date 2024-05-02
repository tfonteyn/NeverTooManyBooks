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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;

public interface AltEdition
        extends Parcelable {

    @Nullable
    String getIsbn();

    @Nullable
    String getPublisher();

    @Nullable
    String getLangIso3();

    /**
     * Get a single cover image of the specified size.
     *
     * @param context      Current context
     * @param searchEngine to use
     * @param cIdx         0..n image index
     * @param size         of image to get.
     *
     * @return fileSpec
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @NonNull
    Optional<String> searchCover(@NonNull Context context,
                                 @NonNull SearchEngine.CoverByIsbn searchEngine,
                                 @IntRange(from = 0, to = 1) int cIdx,
                                 @Nullable Size size)
            throws SearchException, CredentialsException, StorageException;
}

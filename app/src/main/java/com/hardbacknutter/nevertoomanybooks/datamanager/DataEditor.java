/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;

/**
 * Interface supported by an editor object.
 *
 * @param <T> type of {@link DataManager}
 */
public interface DataEditor<T extends DataManager> {

    /** A non-null replacement for {@link Fragment#getTag()}. */
    @NonNull
    FragmentId getFragmentId();

    /** {@link Fragment#isResumed()}. */
    boolean isResumed();

    /**
     * Save the contents of all Fields to the {@link DataManager}.
     *
     * @param target to save the data to
     */
    void onSaveFields(@NonNull T target);

    /**
     * Check for unfinished user edits.
     * <p>
     * Independent of the data stored in {@link #onSaveFields}, an editor
     * can have fields with data in it which are not directly linked with a {@link DataManager}.
     * <p>
     * <strong>Important:</strong> this method can and will access Views when called.
     * It should only be called while a fragment is in resumed state.
     *
     * @return {@code true} if there are
     */
    default boolean hasUnfinishedEdits() {
        return false;
    }
}

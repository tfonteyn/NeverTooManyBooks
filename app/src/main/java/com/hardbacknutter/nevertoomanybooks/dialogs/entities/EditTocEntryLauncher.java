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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class EditTocEntryLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param context        Current context - this <strong>MUST</strong> be a UI context
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    public EditTocEntryLauncher(@NonNull final Context context,
                                @NonNull final String requestKey,
                                @NonNull final ResultListener resultListener) {
        super(context, requestKey);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param tocEntry   the modified entry
     * @param position   the position in the list we we're editing
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final TocEntry tocEntry,
                          final int position) {

        final Bundle result = new Bundle(2);
        result.putParcelable(EditTocEntryViewModel.BKEY_TOC_ENTRY, tocEntry);
        result.putInt(EditTocEntryViewModel.BKEY_POSITION, position);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Constructor.
     *
     * @param book        the entry belongs to
     * @param position    of the tocEntry in the list
     * @param tocEntry    to edit.
     * @param isAnthology Flag that will enable/disable the author edit field
     */
    public void launch(@NonNull final Book book,
                       final int position,
                       @NonNull final TocEntry tocEntry,
                       final boolean isAnthology) {

        final Bundle args = new Bundle(5);
        args.putString(DBKey.TITLE, book.getTitle());
        args.putBoolean(EditTocEntryViewModel.BKEY_ANTHOLOGY, isAnthology);
        args.putParcelable(EditTocEntryViewModel.BKEY_TOC_ENTRY, tocEntry);
        args.putInt(EditTocEntryViewModel.BKEY_POSITION, position);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(
                Objects.requireNonNull(result.getParcelable(EditTocEntryViewModel.BKEY_TOC_ENTRY),
                                       EditTocEntryViewModel.BKEY_TOC_ENTRY),
                result.getInt(EditTocEntryViewModel.BKEY_POSITION));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param tocEntry the modified entry
         * @param position the position in the list we we're editing
         */
        void onResult(@NonNull TocEntry tocEntry,
                      int position);
    }
}

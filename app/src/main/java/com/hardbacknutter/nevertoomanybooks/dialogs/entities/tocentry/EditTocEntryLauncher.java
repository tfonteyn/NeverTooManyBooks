/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.tocentry;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.UiContext;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class EditTocEntryLauncher
        extends DialogLauncher {

    private static final String TAG = "EditTocEntryLauncher";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_POSITION = TAG + ":pos";

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param resultListener to use
     */
    public EditTocEntryLauncher(@NonNull final ResultListener resultListener) {
        super(DBKey.FK_TOC_ENTRY,
              EditTocEntryDialogFragment::new,
              EditTocEntryBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param tocEntry   the modified entry
     * @param position   the position in the list we're editing
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final TocEntry tocEntry,
                          final int position) {

        final Bundle result = new Bundle(2);
        result.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        result.putInt(BKEY_POSITION, position);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Constructor.
     *
     * @param context     preferably the {@code Activity}
     *                    but another UI {@code Context} will also do.
     * @param book        the entry belongs to
     * @param position    of the tocEntry in the list
     * @param tocEntry    to edit.
     * @param isAnthology Flag that will enable/disable the author edit field
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final Book book,
                       final int position,
                       @NonNull final TocEntry tocEntry,
                       final boolean isAnthology) {

        final EditTocEntryInput input = new EditTocEntryInput(
                getRequestKey(), book.getTitle(), position, tocEntry, isAnthology);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        //noinspection deprecation
        resultListener.onResult(
                Objects.requireNonNull(result.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY),
                result.getInt(BKEY_POSITION));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param tocEntry the modified entry
         * @param position the position in the list we were editing
         */
        void onResult(@NonNull TocEntry tocEntry,
                      int position);
    }
}

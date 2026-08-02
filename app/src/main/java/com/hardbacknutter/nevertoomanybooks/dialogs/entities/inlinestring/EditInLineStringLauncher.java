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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.OnEditListener;

/**
 * Launcher for one of the inline-string fields in the Books table.
 * <ul>
 * <li>used for direct/in-place editing of an inline field text; e.g. Book Colour, Format...</li>
 * <li>modifications <strong>ARE STORED</strong> in the database</li>
 * <li>returns the original and the modified/stored text</li>
 * </ul>
 */
public class EditInLineStringLauncher
        extends DialogLauncher {

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private OnEditListener<String> onEditListener;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     *                            Typically, the {@code DBKey} for the column we're editing.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     */
    public EditInLineStringLauncher(@NonNull final String requestKey,
                                    @NonNull final Supplier<DialogFragment> dialogSupplier,
                                    @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
    }

    public void setOnEditListener(@NonNull final OnEditListener<String> listener) {
        this.onEditListener = listener;
    }

    /**
     * Launch the dialog.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param text    to edit.
     */
    public void edit(@NonNull @UiContext final Context context,
                     @NonNull final String text) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        final EditInLineStringInput input = new EditInLineStringInput(getRequestKey(), text);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Output output = Output.fromBundle(result);
        onEditListener.onEdit(output.getOriginal(), output.getEdited());
    }

    static class Output
            implements LauncherOutput {

        private static final String TAG = "Output";
        private static final String BKEY_ORIGINAL = TAG + ":original";
        private static final String BKEY_EDIT = TAG + ":edit";

        @NonNull
        private final String original;
        @NonNull
        private final String edited;

        /**
         * Constructor.
         *
         * @param original the previous value
         * @param edited   the new value
         */
        Output(@NonNull final String original,
               @NonNull final String edited) {
            this.original = original;
            this.edited = edited;
        }

        @NonNull
        static Output fromBundle(@NonNull final Bundle args) {
            final String previousValue = Objects.requireNonNull(
                    args.getString(BKEY_ORIGINAL), BKEY_ORIGINAL);
            final String currentValue = Objects.requireNonNull(
                    args.getString(BKEY_EDIT), BKEY_EDIT);

            return new Output(previousValue, currentValue);
        }

        @NonNull
        public Bundle toBundle() {
            final Bundle result = new Bundle(2);
            result.putString(BKEY_ORIGINAL, original);
            result.putString(BKEY_EDIT, edited);

            return result;
        }

        @NonNull
        String getOriginal() {
            return original;
        }

        @NonNull
        String getEdited() {
            return edited;
        }
    }
}

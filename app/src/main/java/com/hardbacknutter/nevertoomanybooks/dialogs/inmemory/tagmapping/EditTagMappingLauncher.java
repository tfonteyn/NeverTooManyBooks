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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

public class EditTagMappingLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener to use
     */
    public EditTagMappingLauncher(@NonNull final String requestKey,
                                  @NonNull final ResultListener resultListener) {
        super(requestKey,
              EditTagMappingDialogFragment::new,
              EditTagMappingBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param edit    the value to edit
     * @param extras  (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final TagMapping edit,
                       @Nullable final Bundle extras) {

        final EditTagMappingInput input = new EditTagMappingInput(getRequestKey(), edit, extras);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final EditTagMappingOutput output = EditTagMappingOutput.fromBundle(result);
        resultListener.onResult(output.getOriginal(), output.getEdited(), output.getExtras());
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's entry.
         *
         * @param previousValue the previous value
         * @param currentValue  the new value
         * @param extras        (optional) Bundle as provided to one of the
         *                      {@code Launcher#launch} methods
         */
        void onResult(@NonNull TagMapping previousValue,
                      @NonNull TagMapping currentValue,
                      @Nullable Bundle extras);
    }
}

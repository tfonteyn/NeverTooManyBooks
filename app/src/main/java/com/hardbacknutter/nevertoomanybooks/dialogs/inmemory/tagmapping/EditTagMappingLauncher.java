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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

public class EditTagMappingLauncher
        extends DialogLauncher {

    private static final String TAG = "EditTagMappingLauncher";

    static final String BKEY_EXTRAS = TAG + ":extras";

    static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_ORIGINAL = TAG + ":original";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public EditTagMappingLauncher(@NonNull final String requestKey) {
        super(requestKey,
              EditTagMappingDialogFragment::new,
              EditTagMappingBottomSheet::new);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment      the calling DialogFragment
     * @param requestKey    to use
     * @param previousValue the previous value
     * @param currentValue  the new value
     * @param extras        (optional) Bundle as provided to {@link #launch}
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "TypeMayBeWeakened"})
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final TagMapping previousValue,
                          @NonNull final TagMapping currentValue,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        result.putParcelable(BKEY_ORIGINAL, previousValue);
        result.putParcelable(BKEY_EDIT, currentValue);
        if (extras != null && !extras.isEmpty()) {
            result.putBundle(BKEY_EXTRAS, extras);
        }
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Set the results listener.
     *
     * @param resultListener to use
     */
    public void setResultListener(@NonNull final ResultListener resultListener) {
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
    @SuppressWarnings("TypeMayBeWeakened")
    public void launch(@NonNull final Context context,
                       @NonNull final TagMapping edit,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putParcelable(BKEY_EDIT, edit);

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }
        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        resultListener.onResult(
                Objects.requireNonNull(result.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL),
                Objects.requireNonNull(result.getParcelable(BKEY_EDIT), BKEY_EDIT),
                result.getBundle(BKEY_EXTRAS));
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

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

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;

/**
 * Base Dialog class to edit an <strong>in-line String field</strong> in Books table.
 */
public abstract class EditStringDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditStringBaseDialog";

    @StringRes
    private final int dialogTitleId;
    @StringRes
    private final int labelResId;

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** View Binding. */
    private DialogEditStringContentBinding vb;
    /** The text we're editing. */
    private String originalText;
    /** Current edit. */
    private String currentText;

    /**
     * Constructor; only used by the child class no-args constructor.
     *
     * @param dialogTitleId for the dialog (i.e. the toolbar)
     * @param labelResId    to use for the 'hint' of the input field
     */
    EditStringDialogFragment(@StringRes final int dialogTitleId,
                             @StringRes final int labelResId) {
        super(R.layout.dialog_edit_string, R.layout.dialog_edit_string_content);

        this.dialogTitleId = dialogTitleId;
        this.labelResId = labelResId;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        originalText = args.getString(Launcher.BKEY_TEXT, "");

        if (savedInstanceState == null) {
            currentText = originalText;
        } else {
            currentText = savedInstanceState.getString(Launcher.BKEY_TEXT, "");
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditStringContentBinding.bind(view.findViewById(R.id.dialog_content));
        setTitle(dialogTitleId);

        vb.lblEditString.setHint(getString(labelResId));
        vb.editString.setText(currentText);
        autoRemoveError(vb.editString, vb.lblEditString);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        vb.editString.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, getList());
        vb.editString.setAdapter(adapter);

        vb.editString.requestFocus();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Get the (optional) list of strings for the auto-complete.
     *
     * @return list
     */
    @NonNull
    protected List<String> getList() {
        return new ArrayList<>();
    }

    private boolean saveChanges() {
        viewToModel();
        if (currentText.isEmpty()) {
            vb.lblEditString.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (currentText.equals(originalText)) {
            return true;
        }

        final String storedText = onSave(originalText, currentText);
        Launcher.setResult(this, requestKey, originalText, storedText);
        return true;
    }

    private void viewToModel() {
        currentText = vb.editString.getText().toString().trim();
    }

    /**
     * Save the modifications to the database.
     *
     * @param originalText the original text which was passed in to be edited
     * @param currentText  the modified text
     *
     * @return the text as <strong>stored</strong> which can be different from
     *         the modified text passed in.
     */
    @NonNull
    abstract String onSave(@NonNull String originalText,
                           @NonNull String currentText);

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Launcher.BKEY_TEXT, currentText);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    /**
     * Launcher for one of the inline-string fields in the Books table.
     * <ul>
     * <li>used for direct/in-place editing of an inline field text; e.g. Book Color, Format...</li>
     * <li>modifications ARE STORED in the database</li>
     * <li>returns the original and the modified/stored text</li>
     * </ul>
     */
    public static class Launcher
            extends DialogLauncher {

        private static final String TAG = "Launcher";

        /** Input value: the text (String) to edit. */
        static final String BKEY_TEXT = TAG + ":text";

        /** Return value: the modified text. */
        private static final String MODIFIED = TAG + ":m";

        @NonNull
        private final ResultListener resultListener;

        /**
         * Constructor.
         *
         * @param requestKey     FragmentResultListener request key to use for our response.
         *                       Typically the {@code DBKey} for the column we're editing.
         * @param dialogSupplier a supplier for a new DialogFragment
         * @param resultListener callback for results
         */
        public Launcher(@NonNull final String requestKey,
                        @NonNull final Supplier<DialogFragment> dialogSupplier,
                        @NonNull final ResultListener resultListener) {
            super(requestKey, dialogSupplier);
            this.resultListener = resultListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment   the calling DialogFragment
         * @param requestKey to use
         * @param original   the original text which was passed in to be edited
         * @param modified   the modified text
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final String original,
                              @NonNull final String modified) {
            final Bundle result = new Bundle(2);
            result.putString(BKEY_TEXT, original);
            result.putString(MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param text to edit.
         */
        public void launch(@NonNull final String text) {
            final Bundle args = new Bundle(2);
            args.putString(BKEY_TEXT, text);

            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(Objects.requireNonNull(result.getString(BKEY_TEXT), BKEY_TEXT),
                                    Objects.requireNonNull(result.getString(MODIFIED), MODIFIED));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler - modifying an existing item.
             *
             * @param original the original item
             * @param modified the modified item
             */
            void onResult(@NonNull String original,
                          @NonNull String modified);
        }
    }
}

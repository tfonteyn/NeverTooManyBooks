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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ReadProgress;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookReadProgressContentBinding;

public class ReadProgressDialogFragment
        extends FFBaseDialogFragment {

    private static final String TAG = "ReadProgressDialogFrg";

    private ReadProgress readProgress;

    private DialogBookReadProgressContentBinding vb;
    private String requestKey;

    /**
     * No-arg constructor for OS use.
     */
    public ReadProgressDialogFragment() {
        super(R.layout.dialog_book_read_progress,
              R.layout.dialog_book_read_progress_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        requestKey = args.getString(EditLauncher.BKEY_REQUEST_KEY);

        if (savedInstanceState != null) {
            args = savedInstanceState;
        }

        readProgress = Objects.requireNonNull(args.getParcelable(
                Launcher.BKEY_PROGRESS));
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogBookReadProgressContentBinding.bind(view.findViewById(R.id.dialog_content));

        updateUI();

        vb.rbGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    readProgress.setAsPercentage(checkedId == R.id.rb_percentage);
                    updateUI();
                });
    }

    private void updateUI() {
        if (readProgress.asPercentage()) {
            vb.rbPercentage.setChecked(true);
            vb.lblPercentage.setVisibility(View.VISIBLE);
            vb.lblCurrentPage.setVisibility(View.GONE);
            vb.lblTotalPages.setVisibility(View.GONE);

            vb.percentage.requestFocus();
        } else {
            vb.rbPages.setChecked(true);
            vb.lblPercentage.setVisibility(View.GONE);
            vb.lblCurrentPage.setVisibility(View.VISIBLE);
            vb.lblTotalPages.setVisibility(View.VISIBLE);

            vb.currentPage.requestFocus();
        }

        vb.percentage.setText(String.valueOf(readProgress.getPercentage()));
        vb.currentPage.setText(String.valueOf(readProgress.getCurrentPage()));
        vb.totalPages.setText(String.valueOf(readProgress.getTotalPages()));
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_neutral) {
                // Finished reading
                Launcher.setResult(this, requestKey, true);
                dismiss();
                return true;
            } else if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();
        Launcher.setResult(this, requestKey, readProgress);
        return true;
    }

    private void viewToModel() {
        try {
            //noinspection DataFlowIssue
            final String txt = vb.percentage.getText().toString().trim();
            readProgress.setPercentage(txt.isEmpty() ? null : Integer.parseInt(txt));
        } catch (@NonNull final NumberFormatException ignore) {
            readProgress.setPercentage(null);
        }
        try {
            //noinspection DataFlowIssue
            final String txt = vb.currentPage.getText().toString().trim();
            readProgress.setCurrentPage(txt.isEmpty() ? null : Integer.parseInt(txt));
        } catch (@NonNull final NumberFormatException ignore) {
            readProgress.setCurrentPage(null);
        }
        try {
            //noinspection DataFlowIssue
            final String txt = vb.totalPages.getText().toString().trim();
            readProgress.setTotalPages(txt.isEmpty() ? null : Integer.parseInt(txt));
        } catch (@NonNull final NumberFormatException ignore) {
            readProgress.setTotalPages(null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(Launcher.BKEY_PROGRESS, readProgress);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public static class Launcher
            extends EditLauncher {

        static final String BKEY_PROGRESS = TAG + ":progress";

        @NonNull
        private final OnModifiedCallback onModifiedCallback;

        public Launcher(@NonNull final OnModifiedCallback onModifiedCallback) {
            super(DBKey.READ_PROGRESS, ReadProgressDialogFragment::new);
            this.onModifiedCallback = onModifiedCallback;
        }

        public static void setResult(@NonNull final Fragment fragment,
                                     @NonNull final String requestKey,
                                     final boolean read) {
            final Bundle result = new Bundle(1);
            result.putBoolean(DBKey.READ__BOOL, read);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public static void setResult(@NonNull final Fragment fragment,
                                     @NonNull final String requestKey,
                                     @Nullable final ReadProgress readProgress) {
            final Bundle result = new Bundle(1);
            result.putParcelable(BKEY_PROGRESS, readProgress);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void launch(@NonNull final ReadProgress readProgress) {
            final Bundle args = new Bundle(2);
            args.putParcelable(BKEY_PROGRESS, readProgress);
            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            if (result.containsKey(DBKey.READ__BOOL)) {
                onModifiedCallback.onModified(requestKey, result.getBoolean(DBKey.READ__BOOL));
            } else {
                onModifiedCallback.onModified(requestKey, Objects.requireNonNull(
                        result.getParcelable(BKEY_PROGRESS)));

            }
        }

        public interface OnModifiedCallback {

            /**
             * Callback handler.
             *
             * @param requestKey the key as passed in
             * @param read       flag
             */
            void onModified(@NonNull String requestKey,
                            boolean read);

            /**
             * Callback handler.
             *
             * @param requestKey   the key as passed in
             * @param readProgress progress
             */
            void onModified(@NonNull String requestKey,
                            @NonNull ReadProgress readProgress);
        }
    }
}

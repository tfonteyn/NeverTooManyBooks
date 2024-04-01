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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookReadProgressContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;

/**
 * Dialog for the user to update their progress.
 * Supports percentage and "page x of y".
 */
public class ReadingProgressDialogFragment
        extends FFBaseDialogFragment {

    private ReadingProgress readingProgress;

    private DialogBookReadProgressContentBinding vb;
    private String requestKey;

    private final ExtTextWatcher percentageTextWatcher = this::percentageTextToSlider;
    private final ExtTextWatcher currentPageTextWatcher = this::currentPageTextToSlider;
    private final ExtTextWatcher totalPagesTextWatcher = this::totalPagesTextToSlider;


    /**
     * No-arg constructor for OS use.
     */
    public ReadingProgressDialogFragment() {
        super(R.layout.dialog_book_read_progress,
              R.layout.dialog_book_read_progress_content);
    }

    /**
     * Parse the text to an integer.
     *
     * @param text     to parse
     * @param defValue value to return if parsing fails,
     *
     * @return parsed value
     */
    private static int parseInt(@Nullable final Editable text,
                                final int defValue) {
        if (text != null) {
            final String txt = text.toString().trim();
            try {
                if (!txt.isEmpty()) {
                    return Integer.parseInt(txt);
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
        return defValue;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);

        if (savedInstanceState != null) {
            args = savedInstanceState;
        }

        readingProgress = Objects.requireNonNull(args.getParcelable(DBKey.READ_PROGRESS));
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogBookReadProgressContentBinding.bind(view.findViewById(R.id.dialog_content));

        updateUI();
        // Call BEFORE we setup any listeners!
        modelToView();

        vb.rbGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    readingProgress.setAsPercentage(checkedId == R.id.rb_percentage);
                    updateUI();
                });

        addTextWatchers();

        vb.sliderPercentage.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                percentageSliderToText(value);
            }
        });

        vb.sliderPages.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                pagesSliderToText(value);
            }
        });
    }

    private void addTextWatchers() {
        vb.percentage.addTextChangedListener(percentageTextWatcher);
        vb.currentPage.addTextChangedListener(currentPageTextWatcher);
        vb.totalPages.addTextChangedListener(totalPagesTextWatcher);
    }

    private void removeTextWatchers() {
        vb.percentage.removeTextChangedListener(percentageTextWatcher);
        vb.currentPage.removeTextChangedListener(currentPageTextWatcher);
        vb.totalPages.removeTextChangedListener(totalPagesTextWatcher);
    }

    private void updateUI() {
        if (readingProgress.asPercentage()) {
            vb.rbPercentage.setChecked(true);
            vb.lblPercentage.setVisibility(View.VISIBLE);
            vb.sliderPercentage.setVisibility(View.VISIBLE);
            vb.lblCurrentPage.setVisibility(View.GONE);
            vb.lblTotalPages.setVisibility(View.GONE);
            vb.sliderPages.setVisibility(View.GONE);

            vb.percentage.requestFocus();
        } else {
            vb.rbPages.setChecked(true);
            vb.lblPercentage.setVisibility(View.GONE);
            vb.sliderPercentage.setVisibility(View.GONE);
            vb.lblCurrentPage.setVisibility(View.VISIBLE);
            vb.lblTotalPages.setVisibility(View.VISIBLE);
            vb.sliderPages.setVisibility(View.VISIBLE);

            vb.currentPage.requestFocus();
        }
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
        Launcher.setResult(this, requestKey, readingProgress);
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.READ_PROGRESS, readingProgress);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    /**
     * Copy the values as held in the model to the various UI widgets.
     * <p>
     * Note we do NOT even try to smooth out percentage to/from page values!
     */
    private void modelToView() {
        final int percentage = readingProgress.getPercentage();
        vb.percentage.setText(percentage == 0 ? "" : String.valueOf(percentage));
        vb.sliderPercentage.setValue(percentage);


        int currentPage = readingProgress.getCurrentPage();
        int totalPages = readingProgress.getTotalPages();

        // sanity check, should never happen... flw
        if (currentPage < 0) {
            currentPage = 0;
        }
        // sanity check, should never happen... flw
        if (totalPages < 1) {
            totalPages = 1;
        }

        // INCREASE the total-pages value if needed
        if (currentPage > totalPages) {
            totalPages = currentPage;
        }

        vb.currentPage.setText(currentPage == 0 ? "" : String.valueOf(currentPage));
        vb.totalPages.setText(String.valueOf(totalPages));

        // Adjust the upper limit first or we might crash!
        vb.sliderPages.setValueTo(totalPages);
        vb.sliderPages.setValue(currentPage);
    }

    private void viewToModel() {
        readingProgress.setPercentage(parseInt(vb.percentage.getText(), 0));
        readingProgress.setPages(parseInt(vb.currentPage.getText(), 0),
                                 parseInt(vb.totalPages.getText(), 1));
    }

    private void percentageSliderToText(final float value) {
        removeTextWatchers();
        vb.percentage.setText(((int) value) == 0 ? "" : String.valueOf((int) value));
        addTextWatchers();
    }

    private void pagesSliderToText(final float value) {
        removeTextWatchers();
        vb.currentPage.setText(((int) value) == 0 ? "" : String.valueOf((int) value));
        // copy the ValueTo as well to recover from error situations (see #totalPagesTextToSlider)
        vb.totalPages.setText(String.valueOf((int) vb.sliderPages.getValueTo()));
        vb.lblTotalPages.setError(null);
        vb.btnPositive.setEnabled(true);
        addTextWatchers();
    }

    @SuppressLint("SetTextI18n")
    private void percentageTextToSlider(@Nullable final Editable s) {
        removeTextWatchers();
        int value = parseInt(s, 0);
        // only call setText for illegal values
        if (value < 0) {
            value = 0;
            vb.percentage.setText("");
        } else if (value > 100) {
            value = 100;
            vb.percentage.setText("100");
        }
        vb.sliderPercentage.setValue(value);
        addTextWatchers();
    }

    private void currentPageTextToSlider(@Nullable final Editable s) {
        removeTextWatchers();

        int currentPage = parseInt(s, 0);
        int totalPages = parseInt(vb.totalPages.getText(), 1);

        boolean updateTotal = false;

        // sanity check, should never happen... flw
        if (currentPage < 0) {
            currentPage = 0;
            vb.currentPage.setText("");
        }
        // sanity check, should never happen... flw
        if (totalPages < 1) {
            totalPages = 1;
            updateTotal = true;
        }

        // INCREASE the total-pages value if needed
        if (currentPage > totalPages) {
            totalPages = currentPage;
            updateTotal = true;
        }

        if (updateTotal) {
            vb.totalPages.setText(String.valueOf(totalPages));
            vb.lblTotalPages.setError(null);
            vb.btnPositive.setEnabled(true);
        }

        // Adjust the upper limit first or we might crash!
        vb.sliderPages.setValueTo(totalPages);
        vb.sliderPages.setValue(currentPage);

        addTextWatchers();
    }

    private void totalPagesTextToSlider(@Nullable final Editable s) {
        removeTextWatchers();

        int currentPage = parseInt(vb.currentPage.getText(), 0);
        int totalPages = parseInt(s, 1);

        // sanity check, should never happen... flw
        if (currentPage < 0) {
            currentPage = 0;
            vb.currentPage.setText("");
        }
        // sanity check, should never happen... flw
        if (totalPages < 1) {
            totalPages = 1;
            vb.totalPages.setText("1");
        }

        // DO NOT automatically update the current-page.
        if (currentPage > totalPages) {
            vb.lblTotalPages.setError(
                    getString(R.string.error_total_pages_must_be_larger_than_current_page));
            vb.btnPositive.setEnabled(false);
            // do NOT update the slider!
        } else {
            vb.lblTotalPages.setError(null);
            vb.btnPositive.setEnabled(true);

            // Adjust the upper limit first or we might crash!
            vb.sliderPages.setValueTo(totalPages);
            vb.sliderPages.setValue(currentPage);
        }

        addTextWatchers();
    }

    public static class Launcher
            extends DialogLauncher {

        @NonNull
        private final OnReadListener onReadListener;
        @NonNull
        private final OnReadingProgressListener onReadingProgressListener;


        /**
         * Constructor.
         *
         * @param onReadListener            listener for Read/Unread status updates
         * @param onReadingProgressListener listener for extended progress updates
         */
        public Launcher(@NonNull final OnReadListener onReadListener,
                        @NonNull final OnReadingProgressListener onReadingProgressListener) {
            super(DBKey.READ_PROGRESS, ReadingProgressDialogFragment::new);
            this.onReadListener = onReadListener;
            this.onReadingProgressListener = onReadingProgressListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment   the calling DialogFragment
         * @param requestKey to use
         * @param read       Read/Unread status
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "SameParameterValue"})
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              final boolean read) {
            final Bundle result = new Bundle(1);
            result.putBoolean(DBKey.READ__BOOL, read);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment        the calling DialogFragment
         * @param requestKey      to use
         * @param readingProgress data
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @Nullable final ReadingProgress readingProgress) {
            final Bundle result = new Bundle(1);
            result.putParcelable(DBKey.READ_PROGRESS, readingProgress);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param readingProgress to edit
         */
        public void launch(@NonNull final ReadingProgress readingProgress) {
            final Bundle args = new Bundle(2);
            args.putParcelable(DBKey.READ_PROGRESS, readingProgress);

            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            if (result.containsKey(DBKey.READ__BOOL)) {
                onReadListener.onRead(result.getBoolean(DBKey.READ__BOOL));
            } else {
                onReadingProgressListener.onReadingProgress(
                        Objects.requireNonNull(result.getParcelable(DBKey.READ_PROGRESS),
                                               DBKey.READ_PROGRESS));

            }
        }

        @FunctionalInterface
        public interface OnReadListener {

            /**
             * Callback handler.
             *
             * @param read flag
             */
            void onRead(boolean read);
        }

        @FunctionalInterface
        public interface OnReadingProgressListener {

            /**
             * Callback handler.
             *
             * @param readingProgress progress
             */
            void onReadingProgress(@NonNull ReadingProgress readingProgress);
        }
    }
}

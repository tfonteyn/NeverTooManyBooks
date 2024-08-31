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
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookReadProgressContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;

/**
 * Dialog for the user to update their progress.
 * Supports percentage and "page x of y".
 * <p>
 * 2024-08-02: Android Studio is completely [censored]ing up the code formatting in this class!
 * Each time we format the code, methods and variables jump around.
 * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
 */
class ReadingProgressDelegate
        implements FlexDialogDelegate {

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    @NonNull
    private final ReadingProgressViewModel vm;
    private DialogBookReadProgressContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    private final ExtTextWatcher percentageTextWatcher = this::percentageTextToSlider;
    private final ExtTextWatcher currentPageTextWatcher = this::currentPageTextToSlider;
    private final ExtTextWatcher totalPagesTextWatcher = this::totalPagesTextToSlider;

    ReadingProgressDelegate(@NonNull final DialogFragment owner,
                            @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(ReadingProgressViewModel.class);
        vm.init(args);
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

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogBookReadProgressContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_book_read_progress, container, false);
        vb = DialogBookReadProgressContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        // URGENT: the toolbar does not really need a "save" button for the BottomSheet any longer.
        //  keeping it for now for consistency, but we need to review ALL BottomSheet toolbars.
        if (toolbar != null) {
            initToolbar(owner, dialogType, toolbar);
        }

        updateUI();
        // Call BEFORE we setup any listeners!
        modelToView();

        vb.rbGroup.setOnCheckedChangeListener((group, checkedId) -> {
            vm.getReadingProgress().setAsPercentage(checkedId == R.id.rb_percentage);
            updateUI();
        });

        addTextWatchers();
        vb.percentage.setOnEditorActionListener(this::textFieldImeDone);
        vb.totalPages.setOnEditorActionListener(this::textFieldImeDone);

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

    /**
     * When the user taps the 'done'/'enter' key while in the percentage or total-pages
     * text field, we save and close the dialog.
     *
     * @param v        The view that was clicked.
     * @param actionId Identifier of the action.
     * @param event    If triggered by an enter key, this is the event;
     *                 otherwise, this is {@code null}.
     *
     * @return Return true if you have consumed the action, else false.
     */
    private boolean textFieldImeDone(@NonNull final TextView v,
                                     final int actionId,
                                     @Nullable final KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideKeyboard(v);
            if (saveChanges()) {
                owner.dismiss();
            }
            return true;
        }
        return false;
    }

    /**
     * Hide the keyboard.
     *
     * @param v a View from which we can get the window token.
     */
    private void hideKeyboard(@NonNull final View v) {
        new SoftwareKeyboardControllerCompat(v).hide();
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
        if (vm.getReadingProgress().asPercentage()) {
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
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_neutral) {
                // Finished reading
                ReadingProgressLauncher.setResult(owner, requestKey, true);
                owner.dismiss();
                return true;
            } else if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();
        ReadingProgressLauncher.setResult(owner, requestKey, vm.getReadingProgress());
        return true;
    }

    /**
     * Copy the values as held in the model to the various UI widgets.
     * <p>
     * Note we do NOT even try to smooth out percentage to/from page values!
     */
    private void modelToView() {
        final int percentage = vm.getReadingProgress().getPercentage();
        vb.percentage.setText(percentage == 0 ? "" : String.valueOf(percentage));
        vb.sliderPercentage.setValue(percentage);

        int currentPage = vm.getReadingProgress().getCurrentPage();
        int totalPages = vm.getReadingProgress().getTotalPages();

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

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        vm.getReadingProgress().setPercentage(parseInt(vb.percentage.getText(), 0));
        vm.getReadingProgress().setPages(parseInt(vb.currentPage.getText(), 0),
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
            final Context context = vb.getRoot().getContext();
            vb.lblTotalPages.setError(
                    context.getString(R.string.error_total_pages_must_be_larger_than_current_page));
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
}

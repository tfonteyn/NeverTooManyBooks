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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogPartialDatePickerContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;

/**
 * DialogFragment class to allow for selection of partial dates.
 * <p>
 * Seems reasonable to disable relevant day/month pickers if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 */
class PartialDatePickerDelegate
        implements FlexDialogDelegate {

    /**
     * The startYear is set for the really extravagant collectors. (⌐⊙_⊙)
     * URGENT: set -3000 when negative years are tested
     *
     * @see <a href="https://en.wikipedia.org/wiki/List_of_languages_by_first_written_account">
     *         wikipedia</a>
     */
    private static final int START_YEAR = 0;

    /**
     * How many years into the future we show the calender for.
     * We set it 10 years in the future for user who want to add books
     * which are announced, but not available yet.
     */
    private static final int FUTURE = 10;

    /**
     * Maximum number of months in a year. Kept as a constant as I'm reasonably
     * sure this will not change in the foreseeable future. (⌐⊙_⊙)
     */
    private static final int MAX_MONTHS = 12;
    /** Maximum number of days in a month. */
    private static final int MAX_DAYS = 31;
    private static final int MIN_DAYS = 28;
    private final List<MaterialButton> months = new ArrayList<>();
    private final List<MaterialButton> days = new ArrayList<>();

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    @NonNull
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;

    private final PartialDatePickerViewModel vm;
    private DialogPartialDatePickerContentBinding vb;

    @Nullable
    private Toolbar toolbar;

    PartialDatePickerDelegate(@NonNull final DialogFragment owner,
                              @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        dialogTitle = args.getString(PartialDatePickerLauncher.BKEY_DIALOG_TITLE,
                                     owner.getString(R.string.action_edit));
        dialogMessage = args.getString(PartialDatePickerLauncher.BKEY_DIALOG_MESSAGE, null);

        vm = new ViewModelProvider(owner).get(PartialDatePickerViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_partial_date_picker_content,
                                           container, false);
        vb = DialogPartialDatePickerContentBinding.bind(view);
        createCalenderButtons();
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_partial_date_picker, container, false);
        vb = DialogPartialDatePickerContentBinding.bind(view.findViewById(R.id.dialog_content));
        createCalenderButtons();
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            toolbar.setTitle(dialogTitle);
        }

        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        setInitialDate();
    }

    private void setInitialDate() {
        final int year;
        final Optional<Integer> oYear = vm.getYear();
        if (oYear.isPresent()) {
            year = oYear.get();
        } else {
            year = LocalDate.now().getYear();
            vm.setYear(year);
        }
        vb.year.setText(String.valueOf(year));
        vb.year.setOnClickListener(v -> showYearPicker());

        vm.getMonth().ifPresent(m -> months.get(m - 1).setChecked(true));
        vm.getDay().ifPresent(d -> days.get(d - 1).setChecked(true));
    }

    private void showYearPicker() {
        final int now = LocalDate.now().getYear();

        final Optional<Integer> oYear = vm.getYear();
        final int year = oYear.orElse(now);
        final boolean yearIsNow = year == now;

        final Context context = owner.getContext();

        //noinspection DataFlowIssue
        final YearGridAdapter adapter =
                new YearGridAdapter(context, year, START_YEAR, now + FUTURE, pick -> {
                    vb.yearSelectorFrame.setVisibility(View.GONE);
                    updateYear(pick);
                });

        vb.yearSelector.setAdapter(adapter);

        // Scroll the current/selected year into view and show the frame
        final int initialPos = adapter.positionForYear(year);
        vb.yearSelector.scrollToPosition(initialPos);
        vb.yearSelectorFrame.setVisibility(View.VISIBLE);

        vb.yearSelector.post(() -> {
            final GridLayoutManager lm = (GridLayoutManager) vb.yearSelector.getLayoutManager();
            // Check if the item is already in view and calculate its height
            //noinspection DataFlowIssue
            final View view = lm.findViewByPosition(initialPos);
            final int itemHeight = view != null ? view.getHeight() : 0;
            final int offset = vb.yearSelector.getHeight() - itemHeight;
            if (yearIsNow) {
                // make 'now' the last position
                lm.scrollToPositionWithOffset(initialPos, offset);
            } else {
                // center the selected year
                lm.scrollToPositionWithOffset(initialPos, offset / 2);
            }
        });

        vb.getRoot().setOnClickListener(v -> hideYearPicker());
        vb.year.setOnClickListener(v -> hideYearPicker());
    }

    private void hideYearPicker() {
        vb.yearSelectorFrame.setVisibility(View.GONE);
        // restore the original
        vb.year.setOnClickListener(v1 -> showYearPicker());
        vb.getRoot().setOnClickListener(null);
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        // the model is already updated by the valueChangeListener.

        if (vm.getDay().isPresent() && vm.getMonth().isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_if_day_set_month_and_year_must_be,
                          Snackbar.LENGTH_LONG).show();
            return false;

        } else if (vm.getMonth().isPresent() && vm.getYear().isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_if_month_set_year_must_be,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        // not using an "isModified" to avoid having to call getCurrentSelection twice
        final PartialDate previousSelection = vm.getPreviousSelection();
        final PartialDate currentSelection = vm.getCurrentSelection();

        // anything actually changed ? If not, we're done.
        if (previousSelection.equals(currentSelection)) {
            return true;
        }

        PartialDatePickerLauncher.setResult(owner, requestKey,
                                            previousSelection,
                                            currentSelection,
                                            vm.getExtras());
        return true;
    }

    private void createCalenderButtons() {
        final LayoutInflater inflater = LayoutInflater.from(owner.getContext());

        for (int day = 0; day < MAX_DAYS; day++) {
            days.add(addButton(inflater, R.layout.partialdate_day,
                               YMD.Day, String.valueOf(day + 1), day));
        }
        vb.flowDays.setReferencedIds(days.stream().mapToInt(View::getId).toArray());

        final Locale locale = vb.getRoot().getResources().getConfiguration().getLocales().get(0);
        for (int month = 0; month < MAX_MONTHS; month++) {
            final String monthAbbr = Month.of(month + 1)
                                          .getDisplayName(TextStyle.SHORT, locale);
            months.add(addButton(inflater, R.layout.partialdate_month,
                                 YMD.Month, monthAbbr, month));
        }
        vb.flowMonths.setReferencedIds(months.stream().mapToInt(View::getId).toArray());
    }

    @NonNull
    private MaterialButton addButton(@NonNull final LayoutInflater inflater,
                                     @LayoutRes final int layoutResId,
                                     @NonNull final YMD ymd,
                                     @NonNull final CharSequence text,
                                     @IntRange(from = 0, to = 30) final int index) {
        final MaterialButton btn = (MaterialButton)
                inflater.inflate(layoutResId, vb.getRoot(), false);
        final int id = View.generateViewId();
        btn.setId(id);
        btn.setText(text);
        btn.setOnClickListener(v -> update(ymd, index));
        vb.dialogContent.addView(btn);
        return btn;
    }

    private void updateYear(@Nullable final Integer year) {
        vb.year.setText(year != null ? String.valueOf(year) : null);
        vm.setYMD(YMD.Year, year);

        vm.getMonth().ifPresent(m -> {
            if (m == 2) {
                updateDaysInMonth();
            }
        });
    }

    /**
     * Set the {@code checked} status and store the value.
     *
     * @param md    month / day
     * @param index of the button to update
     */
    private void update(@NonNull final YMD md,
                        @IntRange(from = 0, to = 30) final int index) {

        final List<MaterialButton> buttons = md == YMD.Month ? months : days;

        final Optional<Integer> oPrevious = vm.getYMD(md);
        if (oPrevious.isPresent()) {
            @IntRange(from = 1)
            final int previous = oPrevious.get();
            // always deselect the previous button

            buttons.get(previous - 1).setChecked(false);

            // did the user tap the selected button?
            // i.e. they DEselected the button?
            if (previous - 1 == index) {
                // we have a "not set" state
                vm.setYMD(md, null);
            } else {
                // new selection
                buttons.get(index).setChecked(true);
                vm.setYMD(md, index + 1);
            }
        } else {
            // new selection
            buttons.get(index).setChecked(true);
            vm.setYMD(md, index + 1);
        }

        if (md == YMD.Month) {
            updateDaysInMonth();
        }
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        // Determine the total days if we have a valid month/year
        int totalDays;
        if (vm.getYear().isPresent() && vm.getMonth().isPresent()) {
            try {
                // Should never throw here, but paranoia...
                final int year = vm.getYear().get();
                final int month = vm.getMonth().get();
                totalDays = LocalDate.of(year, month, 1).lengthOfMonth();
            } catch (@NonNull final DateTimeException e) {
                totalDays = MAX_DAYS;
            }
        } else {
            // allow the user to start inputting with day first.
            totalDays = MAX_DAYS;
        }

        // ok, this is Android being annoying again...
        // It's not possible to change the {@code checked} state of the buttons
        // if they are disabled.


        // Ensure selected day is valid
        final Optional<Integer> oDay = vm.getDay();
        if (oDay.isPresent()) {
            if (oDay.get() > totalDays) {
                for (int i = MIN_DAYS; i <= totalDays; i++) {
                    days.get(i - 1).setEnabled(true);
                }
                update(YMD.Day, totalDays - 1);
            }
        }

        for (int i = MIN_DAYS; i <= MAX_DAYS; i++) {
            days.get(i - 1).setEnabled(i <= totalDays);
        }
    }


    private static class YearGridAdapter
            extends RecyclerView.Adapter<YearGridAdapter.Holder> {

        private final int initYear;
        private final int startYear;
        private final int endYear;
        @NonNull
        private final Consumer<Integer> yearListener;
        private final LayoutInflater inflater;

        YearGridAdapter(@NonNull final Context context,
                        final int initYear,
                        final int startYear,
                        final int endYear,
                        @NonNull final Consumer<Integer> yearListener) {
            inflater = LayoutInflater.from(context);
            this.initYear = initYear;
            this.startYear = startYear;
            this.endYear = endYear;
            this.yearListener = yearListener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final MaterialButton view = (MaterialButton) inflater
                    .inflate(R.layout.partialdate_year, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final int year = startYear + position;
            holder.yearView.setText(String.format(Locale.getDefault(), "%d", year));
            holder.yearView.setOnClickListener(v -> yearListener.accept(year));
            holder.yearView.setChecked(year == initYear);
        }

        @Override
        public int getItemCount() {
            return endYear - startYear;
        }

        int positionForYear(final int year) {
            return year - startYear;
        }

        public static class Holder
                extends RecyclerView.ViewHolder {

            final MaterialButton yearView;

            Holder(@NonNull final MaterialButton itemView) {
                super(itemView);
                this.yearView = itemView;
            }
        }
    }
}

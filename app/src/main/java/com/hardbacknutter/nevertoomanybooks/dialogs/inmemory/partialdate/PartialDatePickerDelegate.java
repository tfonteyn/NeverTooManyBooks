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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.content.Context;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogPartialDatePickerContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;

/**
 * DialogFragment class to allow for selection of partial dates.
 * <p>
 * <strong>WARNING</strong>: when the screen height is {@link ScreenSize.Value#Compact}
 * (i.e. "Small Phone" landscape) we're showing the dialog message as the toolbar subtitle.
 * This limits the message length but allows full use without scrolling on a "Small Phone".
 * The 4WVGA model is however hopeless... we're not even bothering.
 *
 * <p>
 * Seems reasonable to disable relevant day/month pickers if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 * <p>
 * All values for day/month start at {@code 1}.
 * A {@code value < 1} or {@code null} is seen as "not set".
 * <p>
 * Access to the {@link #months} and {@link #days}
 * is {@code .get(month -1)} and {@code .get(day -1)}
 */
class PartialDatePickerDelegate
        implements FlexDialogDelegate {

    /**
     * The startYear is set for the really extravagant collectors. (⌐⊙_⊙)
     *
     * @see <a href="https://en.wikipedia.org/wiki/List_of_languages_by_first_written_account">
     *         wikipedia</a>
     */
    private static final int START_YEAR = -3000;

    /**
     * How many years into the future we show the calendar for.
     * We set it 10 years in the future for user who want to add books
     * which are announced, but not available yet.
     */
    private static final int FUTURE = 10;

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
    private final boolean limitedHeight;

    /**
     * Constructor.
     * <p>
     * Class output: {@link PartialDatePickerLauncher.Output}.
     *
     * @param owner hosting Fragment
     * @param args  all arguments
     */
    PartialDatePickerDelegate(@NonNull final DialogFragment owner,
                              @NonNull final PartialDatePickerInput args) {
        this.owner = owner;
        //noinspection DataFlowIssue
        limitedHeight = isVeryLimitedHeight(owner.getActivity());

        final Context context = owner.requireContext();

        requestKey = args.getRequestKey();
        dialogTitle = args.getDialogTitle(context);
        dialogMessage = args.getDialogMessage(context);

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

        if (limitedHeight) {
            letToolbarOverlapDragHandle(vb.dialogContent, vb.dialogToolbar);
        }

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

        final boolean hasMessage = dialogMessage != null && !dialogMessage.isEmpty();

        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            toolbar.setTitle(dialogTitle);
            if (limitedHeight && hasMessage) {
                toolbar.setSubtitle(dialogMessage);
            }
        }

        if (!limitedHeight && hasMessage) {
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

        vm.getMonthValue().ifPresent(month -> months.get(month - 1).setChecked(true));
        vm.getDayOfMonth().ifPresent(day -> days.get(day - 1).setChecked(true));
    }

    private void showYearPicker() {
        final int now = LocalDate.now().getYear();

        final Optional<Integer> oYear = vm.getYear();
        final int year = oYear.orElse(now);

        final YearGridAdapter adapter =
                new YearGridAdapter(year, START_YEAR, now + FUTURE, pick -> {
                    hideYearPicker();
                    updateYear(pick);
                });

        vb.yearSelector.setAdapter(adapter);

        // Scroll the current/selected year into view
        final int initialPos = adapter.positionForYear(year);
        vb.yearSelector.scrollToPosition(initialPos);
        // and show the frame
        vb.yearSelectorFrame.setVisibility(View.VISIBLE);

        vb.yearSelector.post(() -> {
            final GridLayoutManager lm = (GridLayoutManager) vb.yearSelector.getLayoutManager();
            // Check if the item is already in view and calculate its height
            //noinspection DataFlowIssue
            final View view = lm.findViewByPosition(initialPos);
            // it should already be visible due to the original scroll, but take no risks.
            final int itemHeight = view != null ? view.getHeight() : 0;
            // calculate the offset, to make sure that view will be visible
            final int offset = vb.yearSelector.getHeight() - itemHeight;
            if (year == now) {
                // make 'now' the last position
                lm.scrollToPositionWithOffset(initialPos, offset);
            } else {
                // centre the selected year
                lm.scrollToPositionWithOffset(initialPos, offset / 2);
            }
        });

        // tap on the sides of the year input field
        vb.getRoot().setOnClickListener(v -> hideYearPicker());
        // or on the input field itself to dismiss without picking a year
        vb.year.setOnClickListener(v -> hideYearPicker());
    }

    private void hideYearPicker() {
        vb.yearSelectorFrame.setVisibility(View.GONE);
        // restore the original
        vb.year.setOnClickListener(v -> showYearPicker());
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

        if (vm.getDayOfMonth().isPresent() && vm.getMonthValue().isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_if_day_set_month_and_year_must_be,
                          Snackbar.LENGTH_LONG).show();
            return false;

        } else if (vm.getMonthValue().isPresent() && vm.getYear().isEmpty()) {
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

        new PartialDatePickerLauncher.Output(previousSelection, currentSelection, vm.getExtras())
                .send(owner, requestKey);
        return true;
    }

    private void createCalenderButtons() {
        final LayoutInflater inflater = LayoutInflater.from(owner.getContext());

        for (int day = 1; day <= YMD.MAX_DAYS; day++) {
            days.add(addButton(inflater, R.layout.partial_date_picker_day,
                               YMD.Day, String.valueOf(day),
                               day));
        }
        vb.flowDays.setReferencedIds(days.stream().mapToInt(View::getId).toArray());

        final Locale locale = vb.getRoot().getResources().getConfiguration().getLocales().get(0);
        for (final Month month : Month.values()) {
            months.add(addButton(inflater, R.layout.partial_date_picker_month,
                                 YMD.Month,
                                 month.getDisplayName(TextStyle.SHORT, locale),
                                 month.getValue()));
        }
        vb.flowMonths.setReferencedIds(months.stream().mapToInt(View::getId).toArray());
    }

    @NonNull
    private MaterialButton addButton(@NonNull final LayoutInflater inflater,
                                     @LayoutRes final int layoutResId,
                                     @NonNull final YMD ymd,
                                     @NonNull final CharSequence text,
                                     @IntRange(from = 1, to = YMD.MAX_DAYS) final int monthOrDay) {
        final MaterialButton btn = (MaterialButton)
                inflater.inflate(layoutResId, vb.getRoot(), false);
        btn.setId(View.generateViewId());
        btn.setText(text);
        btn.setOnClickListener(v -> updateMonthOrDay(ymd, monthOrDay));
        vb.dialogContent.addView(btn);
        return btn;
    }

    private void updateYear(@Nullable final Integer year) {
        vb.year.setText(year != null ? String.valueOf(year) : null);
        vm.setYMD(YMD.Year, year);

        vm.getMonthValue().filter(m -> m == YMD.FEBRUARY).ifPresent(m -> updateDaysInMonth());
    }

    /**
     * Set the {@code checked} status and store the value.
     *
     * @param md         month / day
     * @param monthOrDay the number of the day or month
     */
    private void updateMonthOrDay(@NonNull final YMD md,
                                  @IntRange(from = 1, to = YMD.MAX_DAYS) final int monthOrDay) {

        final List<MaterialButton> buttons = md == YMD.Month ? months : days;

        final Optional<Integer> oPrevious = vm.getYMD(md);
        if (oPrevious.isPresent()) {
            @IntRange(from = 1, to = YMD.MAX_DAYS)
            final int previous = oPrevious.get();
            // always deselect the previous button
            buttons.get(previous - 1).setChecked(false);

            // did the user tap the selected button?
            // i.e. they DE-selected the button?
            if (previous == monthOrDay) {
                // we have a "not set" state
                vm.setYMD(md, null);
            } else {
                // new selection
                buttons.get(monthOrDay - 1).setChecked(true);
                vm.setYMD(md, monthOrDay);
            }
        } else {
            // new selection
            buttons.get(monthOrDay - 1).setChecked(true);
            vm.setYMD(md, monthOrDay);
        }

        if (md == YMD.Month) {
            updateDaysInMonth();
        }
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        @IntRange(from = YMD.MIN_DAYS, to = YMD.MAX_DAYS)
        final int daysInMonth = vm.getDaysInMonth();

        // Ensure selected day is valid
        final Optional<Integer> oDay = vm.getDayOfMonth();
        if (oDay.isPresent()) {
            if (oDay.get() > daysInMonth) {
                for (int day = YMD.MIN_DAYS; day <= daysInMonth; day++) {
                    days.get(day - 1).setEnabled(true);
                }
                updateMonthOrDay(YMD.Day, daysInMonth);
            }
        }

        for (int day = YMD.MIN_DAYS; day <= YMD.MAX_DAYS; day++) {
            days.get(day - 1).setEnabled(day <= daysInMonth);
        }
    }
}

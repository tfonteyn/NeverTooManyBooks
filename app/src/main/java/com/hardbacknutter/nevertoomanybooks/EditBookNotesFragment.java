/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.MaterialSpinnerAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment {

    /**
     * The cross validator for read-start and read-end date fields.
     * The error is always shown on the 'end' field.
     */
    private final FieldValidator<String, TextView> mReadStartEndValidator = field -> {
        // we ignore the passed field, so we can use this validator for both fields.
        Field<String, TextView> startField = mFragmentVM.getFields().getField(R.id.read_start);
        Field<String, TextView> endField = mFragmentVM.getFields().getField(R.id.read_end);

        String start = startField.getAccessor().getValue();
        if (start == null || start.isEmpty()) {
            startField.getAccessor().setError(null);
            endField.getAccessor().setError(null);
            return;
        }

        String end = endField.getAccessor().getValue();
        if (end == null || end.isEmpty()) {
            startField.getAccessor().setError(null);
            endField.getAccessor().setError(null);
            return;
        }

        if (start.compareToIgnoreCase(end) > 0) {
            endField.getAccessor().setError(getString(R.string.vldt_read_start_after_end));

        } else {
            startField.getAccessor().setError(null);
            endField.getAccessor().setError(null);
        }
    };

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewFocusOrder.fix(view);
    }

    @Override
    public void onResume() {
        // the super will trigger the population of all defined Fields and their Views.
        super.onResume();
        // With all Views populated, (re-)add the helpers

        addReadCheckboxOnClickListener();

        addAutocomplete(R.id.price_paid_currency, mFragmentVM.getPricePaidCurrencyCodes());
        addAutocomplete(R.id.location, mFragmentVM.getLocations());

        addDatePicker(mFragmentVM.getFields().getField(R.id.date_acquired),
                      R.string.lbl_date_acquired, true);

        addDateRangePicker(R.string.lbl_read,
                           R.string.lbl_read_start,
                           mFragmentVM.getFields().getField(R.id.read_start),
                           R.string.lbl_read_end,
                           mFragmentVM.getFields().getField(R.id.read_end),
                           true);
    }

    @Override
    protected void onInitFields() {
        super.onInitFields();
        final Fields fields = mFragmentVM.getFields();

        fields.add(R.id.cbx_read, new CompoundButtonAccessor(), DBDefinitions.KEY_READ);
        fields.add(R.id.cbx_signed, new CompoundButtonAccessor(), DBDefinitions.KEY_SIGNED);

        fields.add(R.id.rating, new RatingBarAccessor(), DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.notes, new EditTextAccessor<>(), DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        // MUST be defined before the currency.
        fields.add(R.id.price_paid, new DecimalEditTextAccessor(new DoubleNumberFormatter()),
                   DBDefinitions.KEY_PRICE_PAID);
        fields.add(R.id.price_paid_currency, new EditTextAccessor<>(),
                   DBDefinitions.KEY_PRICE_PAID_CURRENCY)
              .setRelatedFields(R.id.lbl_price_paid,
                                R.id.lbl_price_paid_currency, R.id.price_paid_currency);

        //noinspection ConstantConditions
        fields.add(R.id.condition,
                   new MaterialSpinnerAccessor(getContext(), R.array.conditions_book),
                   DBDefinitions.KEY_BOOK_CONDITION)
              .setRelatedFields(R.id.lbl_condition);
        fields.add(R.id.condition_cover,
                   new MaterialSpinnerAccessor(getContext(), R.array.conditions_dust_cover),
                   DBDefinitions.KEY_BOOK_CONDITION_COVER)
              .setRelatedFields(R.id.lbl_condition_cover);

        fields.add(R.id.location, new EditTextAccessor<String>(), DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.edition,
                   new BitmaskChipGroupAccessor(Book.Edition.getEditions(getContext()), true),
                   DBDefinitions.KEY_EDITION_BITMASK)
              .setRelatedFields(R.id.lbl_edition);

        fields.add(R.id.date_acquired, new TextViewAccessor<>(new DateFieldFormatter()),
                   DBDefinitions.KEY_DATE_ACQUIRED)
              .setTextInputLayout(R.id.lbl_date_acquired);

        fields.add(R.id.read_start, new TextViewAccessor<>(new DateFieldFormatter()),
                   DBDefinitions.KEY_READ_START)
              .setTextInputLayout(R.id.lbl_read_start)
              .setFieldValidator(mReadStartEndValidator);

        fields.add(R.id.read_end, new TextViewAccessor<>(new DateFieldFormatter()),
                   DBDefinitions.KEY_READ_END)
              .setTextInputLayout(R.id.lbl_read_end)
              .setFieldValidator(mReadStartEndValidator);
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), false, false);
    }

    /**
     * Set the OnClickListener for the 'read' fields.
     * <p>
     * When user checks 'read', set the read-end date to today (unless set before)
     */
    private void addReadCheckboxOnClickListener() {
        // only bother when it's in use
        final Field readCbx = mFragmentVM.getFields().getField(R.id.cbx_read);
        //noinspection ConstantConditions
        if (readCbx.isUsed(getContext())) {
            //noinspection ConstantConditions
            readCbx.getAccessor().getView().setOnClickListener(v -> {
                final Checkable cb = (Checkable) v;
                if (cb.isChecked()) {
                    final Field<String, TextView> readEnd =
                            mFragmentVM.getFields().getField(R.id.read_end);
                    if (readEnd.getAccessor().isEmpty()) {
                        final String value = DateUtils.localSqlDateForToday();
                        // Update, display and notify
                        readEnd.getAccessor().setValue(value);
                        readEnd.onChanged(true);
                    }
                }
            });
        }
    }
}

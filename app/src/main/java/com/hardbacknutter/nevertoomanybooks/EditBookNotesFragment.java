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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment {

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void initFields() {
        super.initFields();
        final Fields fields = getFields();

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();
        final FieldFormatter<Number> doubleNumberFormatter = new DoubleNumberFormatter();

        fields.add(R.id.cbx_read, new CompoundButtonAccessor(),
                   DBDefinitions.KEY_READ);
        fields.add(R.id.cbx_signed, new CompoundButtonAccessor(),
                   DBDefinitions.KEY_SIGNED);

        fields.add(R.id.rating, new RatingBarAccessor(),
                   DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.<String>add(R.id.notes, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PRIVATE_NOTES)
                .setRelatedFields(R.id.lbl_notes);

        // MUST be defined before the currency.
        fields.<Number>add(R.id.price_paid, new DecimalEditTextAccessor<>(),
                           DBDefinitions.KEY_PRICE_PAID)
                .setFormatter(doubleNumberFormatter);
        fields.<String>add(R.id.lbl_price_paid_currency, new EditTextAccessor<>(),
                           DBDefinitions.KEY_PRICE_PAID_CURRENCY)
                .setRelatedFields(R.id.lbl_price_paid,
                                  R.id.lbl_price_paid_currency, R.id.price_paid_currency);

        fields.<String>add(R.id.location, new EditTextAccessor<>(),
                           DBDefinitions.KEY_LOCATION)
                .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        //noinspection ConstantConditions
        fields.add(R.id.edition, new BitmaskChipGroupAccessor(
                           Book.Edition.getEditions(getContext()), true),
                   DBDefinitions.KEY_EDITION_BITMASK)
              .setRelatedFields(R.id.lbl_edition);

        fields.<String>add(R.id.date_acquired, new EditTextAccessor<>(),
                           DBDefinitions.KEY_DATE_ACQUIRED)
                .setRelatedFields(R.id.lbl_date_acquired)
                .setFormatter(dateFormatter);

        fields.<String>add(R.id.read_start, new EditTextAccessor<>(),
                           DBDefinitions.KEY_READ_START)
                .setRelatedFields(R.id.lbl_read_start)
                .setFormatter(dateFormatter);

        fields.<String>add(R.id.read_end, new EditTextAccessor<>(),
                           DBDefinitions.KEY_READ_END)
                .setRelatedFields(R.id.lbl_read_end)
                .setFormatter(dateFormatter);
    }

    @Override
    void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), false, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // The views will now have been restored to the fields. (re-)add the helpers

        getFields().getField(R.id.cbx_read).getAccessor().getView().setOnClickListener(v -> {
            // when user sets 'read', also set the read-end date to today (unless set before)
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field<String> readEnd = getFields().getField(R.id.read_end);
                if (readEnd.getAccessor().isEmpty()) {
                    String value = DateUtils.localSqlDateForToday();
                    // Update, display and notify
                    readEnd.getAccessor().setValue(value);
                    readEnd.onChanged();
                }
            }
        });

        addAutocomplete(R.id.price_paid_currency, mBookModel.getPricePaidCurrencyCodes());
        addAutocomplete(R.id.location, mBookModel.getLocations());

        addDatePicker(R.id.date_acquired, R.string.lbl_date_acquired, true);
        addDatePicker(R.id.read_start, R.string.lbl_read_start, true);
        addDatePicker(R.id.read_end, R.string.lbl_read_end, true);
    }
}

/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment<Integer> {

    /** Fragment manager tag. */
    public static final String TAG = "EditBookNotesFragment";

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();
        // ENHANCE: Add a partial date validator. Or not.
        //FieldValidator blankOrDateValidator = new Fields.OrValidator(
        //     new Fields.BlankValidator(), new Fields.DateValidator());

        Field field;

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.add(R.id.read, DBDefinitions.KEY_READ)
              .getView().setOnClickListener(v -> {
            // when user sets 'read', also set the read-end date to today (unless set before)
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field read_end = fields.getField(R.id.read_end);
                if (read_end.isEmpty()) {
                    read_end.setValue(DateUtils.localSqlDateForToday());
                }
            }
        });

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.add(R.id.signed, DBDefinitions.KEY_SIGNED);

        fields.add(R.id.rating, DBDefinitions.KEY_RATING);

        fields.add(R.id.notes, DBDefinitions.KEY_NOTES);

        fields.add(R.id.price_paid, DBDefinitions.KEY_PRICE_PAID);
        field = fields.add(R.id.price_paid_currency, DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_paid_currency,
                        mBookModel.getPricePaidCurrencyCodes());

        field = fields.add(R.id.location, DBDefinitions.KEY_LOCATION);
        initValuePicker(field, R.string.lbl_location, R.id.btn_location, mBookModel.getLocations());

        field = fields.add(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK)
                      .setFormatter(new Fields.BookEditionsFormatter());
        initCheckListEditor(field, R.string.lbl_edition,
                            () -> mBookModel.getBook().getEditableEditionList());

        field = fields.add(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED)
                      .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_date_acquired, true);

        field = fields.add(R.id.read_start, DBDefinitions.KEY_READ_START)
                      .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_read_start, true);

        field = fields.add(R.id.read_end, DBDefinitions.KEY_READ_END)
                      .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_read_end, true);

        fields.addCrossValidator(new Fields.FieldCrossValidator() {
            private static final long serialVersionUID = -3288341939109142352L;

            public void validate(@NonNull final Fields fields,
                                 @NonNull final Bundle values)
                    throws ValidatorException {
                String start = values.getString(DBDefinitions.KEY_READ_START);
                if (start == null || start.isEmpty()) {
                    return;
                }
                String end = values.getString(DBDefinitions.KEY_READ_END);
                if (end == null || end.isEmpty()) {
                    return;
                }
                if (start.compareToIgnoreCase(end) > 0) {
                    throw new ValidatorException(R.string.vldt_read_start_after_end);
                }
            }
        });
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * <ul>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * <li>{@link #onSaveFieldsToBook} from base class onPause</li>
     * </ul>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusSettings.fix(getView());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // hide unwanted fields
        showOrHideFields(false);
    }
}

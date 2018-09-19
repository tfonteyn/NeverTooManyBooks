/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.Date;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class EditBookNotesFragment extends EditBookAbstractFragment implements OnPartialDatePickerListener {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean(UniqueId.BKEY_DIRTY));
            }

            //FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
            FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

            mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING, null);
            mFields.add(R.id.rating_label, "", UniqueId.KEY_BOOK_RATING, null);
            mFields.add(R.id.read, UniqueId.KEY_BOOK_READ, null);
            mFields.add(R.id.notes, UniqueId.KEY_NOTES, null);
            mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED, null);

            /* location
             *  TODO: unify with {@link EditBookFieldsFragment#setupMenuMoreButton}
             */
            {
                mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION, null);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                        android.R.layout.simple_dropdown_item_1line, mEditManager.getLocations());
                mFields.setAdapter(R.id.location, adapter);

                final Field field = mFields.getField(R.id.location);
                // Get the list to use in the AutoComplete stuff
                AutoCompleteTextView textView = (AutoCompleteTextView) field.getView();
                textView.setAdapter(new ArrayAdapter<>(getActivity(),
                        android.R.layout.simple_dropdown_item_1line,
                        mEditManager.getLocations()));
                // Get the drop-down button for the list and setup dialog
                ImageView button = getView().findViewById(R.id.location_button);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(),
                                getString(R.string.location),
                                mEditManager.getLocations(), field.getValue().toString(),
                                new StandardDialogs.SimpleDialogOnClickListener() {
                                    @Override
                                    public void onClick(@NonNull final StandardDialogs.SimpleDialogItem item) {
                                        field.setValue(item.toString());
                                    }
                                });
                    }
                });
            }

            Field field;
            // ENHANCE: Add a partial date validator. Or not.
            field = mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START, null, dateFormatter);
            field.getView().setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance()
                            .setTitle(R.string.read_start)
                            .setDialogId(R.id.read_start); // Set to the destination field ID
                    try {
                        frag.setDate(getDateFrom(R.id.read_start));
                    } catch (Exception ignore) {
                        // use the default date
                    }

                    frag.show(getFragmentManager(), null);
                }
            });

            field = mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END, null, dateFormatter);
            field.getView().setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance()
                            .setTitle(R.string.read_end)
                            .setDialogId(R.id.read_end); // Set to the destination field ID
                    try {
                        frag.setDate(getDateFrom(R.id.read_end));
                    } catch (Exception ignore) {
                        // use the default date
                    }
                    frag.show(getFragmentManager(), null);
                }
            });

            mFields.addCrossValidator(new Fields.FieldCrossValidator() {
                public void validate(@NonNull Fields fields, @NonNull Bundle values) {
                    String start = values.getString(UniqueId.KEY_BOOK_READ_START);
                    if (start == null || start.isEmpty())
                        return;
                    String end = values.getString(UniqueId.KEY_BOOK_READ_END);
                    if (end == null || end.isEmpty())
                        return;
                    if (start.compareToIgnoreCase(end) > 0)
                        throw new ValidatorException(R.string.vldt_read_start_after_end, new Object[]{});
                }
            });

            try {
                ViewUtils.fixFocusSettings(getView());
            } catch (Exception e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.logError(e);
            }

            mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
                @Override
                public void afterFieldChange(@NonNull final Field field, @Nullable final String newValue) {
                    mEditManager.setDirty(true);
                }
            });

            // Setup the background
            //Utils.init(R.drawable.bc_background_gradient_dim, this, false);

        } catch (Exception e) {
            Logger.logError(e);
        }
        Tracker.exitOnCreate(this);
    }

    private String getDateFrom(int fieldResId) {
        Object o = mFields.getField(fieldResId).getValue();
        if (o == null || o.toString().isEmpty()) {
            return DateUtils.toSqlDateTime(new Date());
        } else {
            return o.toString();
        }
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     *
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
        String value = DateUtils.buildPartialDate(year, month, day);
        mFields.getField(dialogId).setValue(value);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onPause() {
        Tracker.enterOnPause(this);
        BookData book = mEditManager.getBookData();
        mFields.getAll(book);
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onLoadBookDetails(@NonNull BookData bookData, boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(bookData);
        // No special handling required; the setAll() done by the caller is enough
        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }

}
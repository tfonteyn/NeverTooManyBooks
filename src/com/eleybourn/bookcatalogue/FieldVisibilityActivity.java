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

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the Field Visibility page. It contains a list of all fields and a
 * checkbox to enable or disable the field on the main edit book screen.
 *
 * Places to add them:
 * {@link BookAbstractFragment#showHideFields(boolean)}
 * {@link BookDetailsFragment#populateReadStatus} and similar show methods in that class
 * or the parent classes
 *
 * Note that the Booklist related preferences do NOT observe visibility of these fields.
 * Modify / Hide / view a list... and unpredictable results might be shown to the user.
 *
 * @author Evan Leybourn
 */
public class FieldVisibilityActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_FIELD_VISIBILITY;

    private static final List<FieldInfo> mFields = new ArrayList<>();

    static {
        mFields.add(new FieldInfo(UniqueId.KEY_AUTHOR_ID, R.string.author, true));
        mFields.add(new FieldInfo(UniqueId.KEY_TITLE, R.string.title, true));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_THUMBNAIL, R.string.thumbnail, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_ISBN, R.string.isbn, false));
        mFields.add(new FieldInfo(UniqueId.KEY_SERIES_NAME, R.string.series, false));
        mFields.add(new FieldInfo(UniqueId.KEY_SERIES_NUM, R.string.series_num, false));
        mFields.add(new FieldInfo(UniqueId.KEY_DESCRIPTION, R.string.description, false));

        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_PUBLISHER, R.string.publisher, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_DATE_PUBLISHED, R.string.first_publication, false));
        mFields.add(new FieldInfo(UniqueId.KEY_FIRST_PUBLICATION, R.string.date_published, false));

        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_FORMAT, R.string.format, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_GENRE, R.string.genre, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_LANGUAGE, R.string.lbl_language, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_PAGES, R.string.pages, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_PRICE_LISTED, R.string.price_listed, false));

        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, R.string.table_of_content, false));

        // **** PERSONAL FIELDS ****
        mFields.add(new FieldInfo(UniqueId.KEY_BOOKSHELF_NAME, R.string.bookshelf, false));
        mFields.add(new FieldInfo(UniqueId.KEY_LOAN_LOANED_TO, R.string.loan, false));
        mFields.add(new FieldInfo(UniqueId.KEY_NOTES, R.string.notes, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_LOCATION, R.string.location_of_book, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_PRICE_PAID, R.string.price_paid, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ, R.string.read, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ_START, R.string.read_start, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ_END, R.string.read_end, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_EDITION_BITMASK, R.string.edition, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_SIGNED, R.string.signed, false));
        mFields.add(new FieldInfo(UniqueId.KEY_BOOK_RATING, R.string.rating, false));

        //NEWKIND: new fields
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_field_visibility;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.menu_manage_fields);
            populateFields();
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /**
     * Build the manage field visibility by adding onClick events to each field checkbox
     */
    private void populateFields() {
        // Display the list of fields
        ViewGroup parent = findViewById(R.id.manage_fields_scrollview);
        for (FieldInfo field : mFields) {
            final String fieldName = field.name;

            CompoundButton cb = new CheckBox(this);
            cb.setChecked(Fields.isVisible(fieldName));
            cb.setTextAppearance(this, android.R.style.TextAppearance_Large);
            cb.setText(field.stringId);

            if (field.compulsory) {
                cb.setEnabled(false);
            } else {
                cb.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Here we actually commit the change to the preferences
                        Fields.setVisibility(fieldName, !Fields.isVisible(fieldName));
                        // so setting dirty has no sense, but leaving this as a reminder!
                        // flag up we have (at least one) modifications
                        //setDirty(true);

                        // setActivityResult() takes care of setting the result when the user does a back-press
                    }
                });
            }

            //Create the LinearLayout to hold the row
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(5, 0, 0, 0);
            layout.addView(cb);

            parent.addView(layout);
        }
    }

    /**
     * For now, always signal that something (might have) changed
     */
    @Override
    protected void setActivityResult() {
        setResult(Activity.RESULT_OK); /* 2f885b11-27f2-40d7-8c8b-fcb4d95a4151 */
    }

    private static class FieldInfo {
        @NonNull
        final String name;
        @StringRes
        final int stringId;
        final boolean compulsory;

        FieldInfo(final @NonNull String name, final @StringRes int stringId, final boolean compulsory) {
            this.name = name;
            this.stringId = stringId;
            this.compulsory = compulsory;
        }
    }
}
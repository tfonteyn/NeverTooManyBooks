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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the Field Visibility page. It contains a list of all fields and a
 * checkbox to enable or disable the field on the main edit book screen.
 *
 * @author Evan Leybourn
 */
public class FieldVisibilityActivity extends BookCatalogueActivity {

    /** Prefix for all preferences */
    public final static String TAG = "field_visibility_";
    private static final List<FieldInfo> allFields = new ArrayList<>();
    static {
        allFields.add(new FieldInfo(UniqueId.KEY_AUTHOR_ID, R.string.author, true));
        allFields.add(new FieldInfo(UniqueId.KEY_TITLE, R.string.title, true));

        allFields.add(new FieldInfo(UniqueId.BKEY_THUMBNAIL, R.string.thumbnail, false));
        allFields.add(new FieldInfo(UniqueId.KEY_ISBN, R.string.isbn, false));
        allFields.add(new FieldInfo(UniqueId.KEY_SERIES_NAME, R.string.series, false));
        allFields.add(new FieldInfo(UniqueId.KEY_SERIES_NUM, R.string.series_num, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_PUBLISHER, R.string.publisher, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_DATE_PUBLISHED, R.string.first_publication, false));
        allFields.add(new FieldInfo(UniqueId.KEY_FIRST_PUBLICATION, R.string.date_published, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOKSHELF_NAME, R.string.bookshelf, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_PAGES, R.string.pages, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_LIST_PRICE, R.string.list_price, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ, R.string.read, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_RATING, R.string.rating, false));
        allFields.add(new FieldInfo(UniqueId.KEY_NOTES, R.string.notes, false));
        allFields.add(new FieldInfo(UniqueId.KEY_ANTHOLOGY_MASK, R.string.anthology, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_LOCATION, R.string.location_of_book, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ_START, R.string.read_start, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_READ_END, R.string.read_end, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_FORMAT, R.string.format, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_SIGNED, R.string.signed, false));
        allFields.add(new FieldInfo(UniqueId.KEY_DESCRIPTION, R.string.description, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_GENRE, R.string.genre, false));
        allFields.add(new FieldInfo(UniqueId.KEY_BOOK_LANGUAGE, R.string.language, false));

        //NEWKIND: when adding fields that can be invisible, add them here
    }

    public static boolean isVisible(@NonNull final String fieldName) {
        return BCPreferences.getBoolean(TAG + fieldName, true);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_field_visibility;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.menu_manage_fields);
            setupFields();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * This function builds the manage field visibility by adding onClick events to each field checkbox
     */
    private void setupFields() {

        SharedPreferences mPrefs = getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, MODE_PRIVATE);

        // Display the list of fields
        LinearLayout parent = findViewById(R.id.manage_fields_scrollview);
        for (FieldInfo field : allFields) {
            final String prefs_name = TAG + field.name;

            CheckBox cb = new CheckBox(this);
            cb.setChecked(mPrefs.getBoolean(prefs_name, true));
            cb.setTextAppearance(this, android.R.style.TextAppearance_Large);
            cb.setText(field.resId);
            //cb.setPadding(0, 5, 0, 0);

            if (field.compulsory) {
                cb.setTextColor(Color.GRAY);
                cb.setEnabled(false);
            } else {
                cb.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences mPrefs = getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, MODE_PRIVATE);
                        SharedPreferences.Editor ed = mPrefs.edit();
                        boolean field_visibility = mPrefs.getBoolean(prefs_name, true);
                        ed.putBoolean(prefs_name, !field_visibility);
                        ed.apply();
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

    private static class FieldInfo {
        final String name;
        final int resId;
        final boolean compulsory;

         FieldInfo(@NonNull final String name, final int resId, final boolean compulsory) {
            this.name = name;
            this.resId = resId;
            this.compulsory = compulsory;
        }
    }
}
/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.booklist;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Edit the properties associated with a passed style
 *
 * @author Philip Warner
 */
public class BooklistStylePropertiesActivity extends BookCatalogueActivity {
    private static final String TAG = "BooklistStyleProperties";
    /** Parameter used to pass data to this activity */
    public static final String BKEY_STYLE = TAG + ".Style";
    /** Parameter used to pass data to this activity */
    private static final String BKEY_SAVE_TO_DATABASE = TAG + ".SaveToDb";

    /** Database connection, if used */
    private CatalogueDBAdapter mDb = null;

    /** Options indicating style should be saved to the database on exit */
    private boolean mSaveToDb = true;
    /** Style we are editing */
    private BooklistStyle mStyle;
    /** Properties object constructed from current style */
    private Properties mProperties;

    @Override
    protected int getLayoutId() {
        return R.layout.booklist_style_properties;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSave();
            }
        });
        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Get the intent and get the style and other details
        Intent intent = this.getIntent();
        mStyle = (BooklistStyle) intent.getSerializableExtra(BKEY_STYLE);

        if (intent.hasExtra(BKEY_SAVE_TO_DATABASE)) {
            mSaveToDb = intent.getBooleanExtra(BKEY_SAVE_TO_DATABASE, true);
        }

        // Display all the style properties
        displayProperties();

        // Make the title
        String title;
        if (mStyle.getDisplayName().isEmpty()) {
            title = getString(R.string.new_style);
        } else if (mStyle.id == 0) {
            title = getString(R.string.clone_style_colon_name, mStyle.getDisplayName());
        } else {
            title = getString(R.string.edit_style_colon_name, mStyle.getDisplayName());
        }

        setTitle(title);

        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(this, R.string.hint_booklist_style_properties, null);
        }
    }

    /**
     * Setup the style properties views based on the current style
     */
    private void displayProperties() {
        ViewGroup vg = this.findViewById(R.id.body);
        vg.removeAllViews();

        mProperties = mStyle.getProperties();
        mProperties.add(new GroupsProperty());
        mProperties.buildView(this.getLayoutInflater(), vg);
    }

    /**
     * Start editing the groups.
     */
    private void startGroupsActivity() {
        Intent i = new Intent(this, BooklistStyleGroupsListActivity.class);
        i.putExtra(BooklistStyleGroupsListActivity.BKEY_STYLE, mStyle);
        i.putExtra(BooklistStyleGroupsListActivity.BKEY_SAVE_TO_DATABASE, false);
        startActivityForResult(i, UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS);
    }

    /**
     * Called when 'save' button is clicked.
     */
    private void handleSave() {
        try {
            mProperties.validate();
        } catch (ValidationException e) {
            StandardDialogs.showQuickNotice(this, e.getLocalizedMessage());
            return;
        }

        if (mSaveToDb) {
            getDb().insertOrUpdateBooklistStyle(mStyle);
        }
        Intent intent = new Intent();
        intent.putExtra(BKEY_STYLE, mStyle);
        setResult(RESULT_OK, intent);
        finish();

    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS:
                // When groups have been edited, copy them to this style.
                if (intent != null && intent.hasExtra(BooklistStyleGroupsListActivity.BKEY_STYLE)) {
                    BooklistStyle editedStyle = null;
                    try {
                        editedStyle = (BooklistStyle) intent.getSerializableExtra(BooklistStyleGroupsListActivity.BKEY_STYLE);
                    } catch (Exception e) {
                        Logger.logError(e);
                    }
                    if (editedStyle != null) {
                        mStyle.setGroups(editedStyle);
                        displayProperties();
                    }
                }
                break;
        }
    }

    /**
     * Get/create database as required.
     */
    @NonNull
    private CatalogueDBAdapter getDb() {
        if (mDb == null) {
            mDb = new CatalogueDBAdapter(this);
        }
        mDb.open();
        return mDb;
    }

    /**
     * Cleanup.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Implementation of a 'Property' that has a button which will start the activity
     * for editing style groups.
     *
     * @author Philip Warner
     */
    private class GroupsProperty extends StringProperty {

        /**
         * Constructor
         */
        GroupsProperty() {
            super("StyleGroups", PropertyGroup.GRP_GENERAL, R.string.groupings);
        }

        /**
         * Get the property 'value': just a list of the groups.
         */
        @Override
        @NonNull
        public String get() {
            return mStyle.getGroupListDisplayNames();
        }

        /**
         * Can not be 'set'. Will be edited via the button->activity.
         */
        @Override
        @NonNull
        public GroupsProperty set(@Nullable final String value) {
            throw new IllegalStateException("Attempt to set read-only property string");
        }

        /**
         * Setup the view for a single line/property
         */
        @NonNull
        @Override
        public View getView(@NonNull final LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.property_value_string_button, null);
            ViewTagger.setTag(v, R.id.TAG_PROPERTY, this);

            final TextView name = v.findViewById(R.id.name);
            name.setText(getName());

            final TextView value = v.findViewById(R.id.value);
            value.setHint(getName());
            value.setText(get());

            final View btn = v.findViewById(R.id.edit_button);
            btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startGroupsActivity();
                }
            });
            return v;
        }
    }
}

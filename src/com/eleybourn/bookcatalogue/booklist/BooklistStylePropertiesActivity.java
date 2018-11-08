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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
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
public class BooklistStylePropertiesActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_PROPERTIES;

    private static final String TAG = "BooklistStyleProperties";
    /** Parameter used to pass data to this activity */
    public static final String REQUEST_BKEY_STYLE = TAG + ".Style";
    /** Parameter used to pass data to this activity */
    private static final String BKEY_SAVE_TO_DATABASE = TAG + ".SaveToDb";

    /** Database connection, if used */
    @Nullable
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
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mProperties.validate();
                } catch (ValidationException e) {
                    StandardDialogs.showUserMessage(BooklistStylePropertiesActivity.this, e.getLocalizedMessage());
                    return;
                }

                if (mSaveToDb) {
                    getDb().insertOrUpdateBooklistStyle(mStyle);
                }
                Intent data = new Intent();
                data.putExtra(REQUEST_BKEY_STYLE, mStyle);
                setResult(Activity.RESULT_OK, data);  /* fadd7b9a-7eaf-4af9-90ce-6ffb7b93afe6 */
                finish();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        // Get the intent and get the style and other details
        Intent intent = this.getIntent();
        mStyle = (BooklistStyle) intent.getSerializableExtra(REQUEST_BKEY_STYLE);

        if (intent.hasExtra(BKEY_SAVE_TO_DATABASE)) {
            mSaveToDb = intent.getBooleanExtra(BKEY_SAVE_TO_DATABASE, true);
        }

        // Display all the style properties
        displayProperties();

        // Make the title
        String title;
        if (mStyle.getDisplayName().isEmpty()) {
            title = getString(R.string.dialog_title_new_style);
        } else if (mStyle.id == 0) {
            title = getString(R.string.dialog_title_clone_style_colon_name, mStyle.getDisplayName());
        } else {
            title = getString(R.string.dialog_title_edit_style_colon_name, mStyle.getDisplayName());
        }

        setTitle(title);

        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_style_properties, null);
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
        Intent intent = new Intent(this, BooklistStyleGroupsActivity.class);
        intent.putExtra(BooklistStyleGroupsActivity.REQUEST_BKEY_STYLE, mStyle);
        intent.putExtra(BooklistStyleGroupsActivity.REQUEST_BKEY_SAVE_TO_DATABASE, false);
        startActivityForResult(intent, BooklistStyleGroupsActivity.REQUEST_CODE); /* 06ed8d0e-7120-47aa-b47e-c0cd46361dcb */
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case BooklistStyleGroupsActivity.REQUEST_CODE: /* 06ed8d0e-7120-47aa-b47e-c0cd46361dcb */
                if (resultCode == Activity.RESULT_OK) {
                    // having a result is optional
                    if (data != null && data.hasExtra(BooklistStyleGroupsActivity.REQUEST_BKEY_STYLE)) {
                        // When groups have been edited, copy them to this style.
                        BooklistStyle editedStyle = null;
                        try {
                            editedStyle = (BooklistStyle) data.getSerializableExtra(BooklistStyleGroupsActivity.REQUEST_BKEY_STYLE);
                        } catch (Exception e) {
                            Logger.error(e);
                        }
                        if (editedStyle != null) {
                            mStyle.setGroups(editedStyle);
                            displayProperties();
                        }
                    }
                }
                return;
        }

        super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
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
        public GroupsProperty set(final @Nullable String value) {
            throw new UnsupportedOperationException("Attempt to set read-only property string");
        }

        /**
         * Setup the view for a single line/property
         */
        @NonNull
        @Override
        public View getView(final @NonNull LayoutInflater inflater) {
            final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_string_with_edit_button, null);
            // create Holder -> not needed here

            // tags used
            ViewTagger.setTag(root, R.id.TAG_PROPERTY, this); // value: GroupsProperty

            // Set the initial values
            final TextView name = root.findViewById(R.id.series);
            name.setText(getName());

            final TextView value = root.findViewById(R.id.value);
            value.setHint(getName());
            value.setText(get());

            // Setup click handlers view row and edit button
            root.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startGroupsActivity();
                        }
                    });
            root.findViewById(R.id.btn_edit).setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startGroupsActivity();
                        }
                    });
            return root;
        }
    }
}

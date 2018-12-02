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
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.properties.PropertyList;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.Objects;

/**
 * Edit the properties associated with a passed style
 *
 * Started from:
 * - {@link BooklistPreferredStylesActivity}
 *
 * Starts
 * - {@link EditBooklistStyleGroupsActivity}
 * Consumes
 * - Activity.RESULT_OK from {@link EditBooklistStyleGroupsActivity}
 *
 *
 * onSave:
 * - setResult(UniqueId.ACTIVITY_RESULT_OK_BooklistStylePropertiesActivity, data);
 *
 * @author Philip Warner
 */
public class EditBooklistStyleActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_PROPERTIES;

    private static final String TAG = "BooklistStyleProperties";
    /** Parameter used to pass data to this activity */
    public static final String REQUEST_BKEY_STYLE = TAG + ".Style";

    /** Database connection, if used */
    @Nullable
    private CatalogueDBAdapter mDb = null;

    /** Style we are editing */
    private BooklistStyle mStyle;
    /** PropertyList object constructed from current style */
    private PropertyList mPropertyList;

    @Override
    protected int getLayoutId() {
        return R.layout.booklist_style_properties;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mStyle = getIntent().getParcelableExtra(REQUEST_BKEY_STYLE);

        displayProperties();

        // Make the title
        String title;
        if (mStyle.getDisplayName().isEmpty()) {
            title = getString(R.string.title_add_style);
        } else if (mStyle.id == 0) {
            title = getString(R.string.title_clone_style_colon_name, mStyle.getDisplayName());
        } else {
            title = getString(R.string.title_edit_style_colon_name, mStyle.getDisplayName());
        }
        setTitle(title);

        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mPropertyList.validate();
                } catch (ValidationException e) {
                    StandardDialogs.showUserMessage(EditBooklistStyleActivity.this, e.getLocalizedMessage());
                    return;
                }

                getDb().insertOrUpdateBooklistStyle(mStyle);

                Intent data = new Intent();
                data.putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
                setResult(UniqueId.ACTIVITY_RESULT_OK_BooklistStylePropertiesActivity, data);  /* fadd7b9a-7eaf-4af9-90ce-6ffb7b93afe6 */
                finish();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finishIfClean();
            }
        });

        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_style_properties, null);
        }

        Tracker.exitOnCreate(this);
    }

    /**
     * Setup the style properties views based on the current style
     */
    private void displayProperties() {
        ViewGroup vg = this.findViewById(R.id.body);
        vg.removeAllViews();

        mPropertyList = mStyle.getProperties();
        mPropertyList.add(new GroupsProperty());
        mPropertyList.buildView(this.getLayoutInflater(), vg);
    }

    /**
     * Start editing the groups.
     */
    private void startGroupsActivity() {
        Intent intent = new Intent(this, EditBooklistStyleGroupsActivity.class);
        intent.putExtra(EditBooklistStyleGroupsActivity.REQUEST_BKEY_STYLE, (Parcelable) mStyle);
        intent.putExtra(EditBooklistStyleGroupsActivity.REQUEST_BKEY_SAVE_TO_DATABASE, false);
        startActivityForResult(intent, EditBooklistStyleGroupsActivity.REQUEST_CODE); /* 06ed8d0e-7120-47aa-b47e-c0cd46361dcb */
    }


    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            case EditBooklistStyleGroupsActivity.REQUEST_CODE: {/* 06ed8d0e-7120-47aa-b47e-c0cd46361dcb */
                switch (resultCode) {
                    case Activity.RESULT_OK: {
                        Objects.requireNonNull(data);
                        // When groups have been edited, copy them to this style.
                        BooklistStyle editedStyle = data.getParcelableExtra(EditBooklistStyleGroupsActivity.REQUEST_BKEY_STYLE);
                        if (editedStyle != null) {
                            mStyle.setGroups(editedStyle);
                            displayProperties();
                        }
                        break;
                    }
                    default:
                        break;
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    /**
     * Get/create database as required.
     */
    @NonNull
    private CatalogueDBAdapter getDb() {
        if (mDb == null) {
            mDb = new CatalogueDBAdapter(this);
        }
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
            super("StyleGroups", PropertyGroup.GRP_GENERAL, R.string.groupings, null);
        }

        /**
         * Get the property 'value': just a list of the groups.
         */
        @Override
        @NonNull
        public String getValue() {
            return mStyle.getGroupListDisplayNames();
        }

        /**
         * Can not be 'set'. Will be edited via the button->activity.
         */
        @Override
        @NonNull
        public GroupsProperty setValue(final @Nullable String value) {
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
            final TextView name = root.findViewById(R.id.name);
            name.setText(getNameResourceId());

            final TextView value = root.findViewById(R.id.value);
            value.setHint(getNameResourceId());
            value.setText(getValue());

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

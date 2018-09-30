/*
 * @copyright 2011 Philip Warner
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

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.baseactivity.ActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * FIXME ... re-test and see why the progress stops
 */
public class UpdateFromInternet extends ActivityWithTasks {

    private final FieldUsages mFieldUsages = new FieldUsages();
    private long mUpdateSenderId = 0;
    private final ManagedTask.TaskListener mThumbnailsHandler = new ManagedTask.TaskListener() {
        @Override
        public void onTaskFinished(@NonNull ManagedTask t) {
            mUpdateSenderId = 0;
            finish();
        }
    };
    private SharedPreferences mPrefs = null;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_update_from_internet;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.update_fields);
            LibraryThingManager.showLtAlertIfNecessary(this, false, "update_from_internet");
            mPrefs = getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, android.content.Context.MODE_PRIVATE);
            setupFields();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param field    name to use in FieldUsages
     * @param visField Field name to check for visibility. If null, use field.
     * @param stringId of field label string
     * @param usage    Usage to apply.
     */
    private void addIfVisible(@NonNull final String field,
                              @Nullable String visField,
                              @StringRes final int stringId,
                              @NonNull final FieldUsages.Usages usage,
                              final boolean canAppend) {
        if (visField == null || visField.trim().isEmpty()) {
            visField = field;
        }

        if (mPrefs.getBoolean(FieldVisibilityActivity.TAG + visField, true)) {
            mFieldUsages.put(new FieldUsages.FieldUsage(field, stringId, usage, canAppend));
        }
    }

    /**
     * This function builds the manage field visibility by adding onClick events
     * to each field checkbox
     */
    private void setupFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, UniqueId.KEY_AUTHOR_ID, R.string.author, FieldUsages.Usages.ADD_EXTRA, true);
        addIfVisible(UniqueId.KEY_TITLE, null, R.string.title, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_ISBN, null, R.string.isbn, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.BKEY_THUMBNAIL, null, R.string.thumbnail, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, UniqueId.KEY_SERIES_NAME, R.string.series, FieldUsages.Usages.ADD_EXTRA, true);
        addIfVisible(UniqueId.KEY_PUBLISHER, null, R.string.publisher, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED, null, R.string.date_published, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_PAGES, null, R.string.pages, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_LIST_PRICE, null, R.string.list_price, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_FORMAT, null, R.string.format, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_DESCRIPTION, null, R.string.description, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_GENRE, null, R.string.genre, FieldUsages.Usages.COPY_IF_BLANK, false);
        addIfVisible(UniqueId.KEY_BOOK_LANGUAGE, null, R.string.language, FieldUsages.Usages.COPY_IF_BLANK, false);

        // Display the list of fields
        LinearLayout parent = findViewById(R.id.manage_fields_scrollview);
        for (FieldUsages.FieldUsage usage : mFieldUsages.values()) {
            //Create the LinearLayout to hold each row
            ViewGroup layout = new LinearLayout(this);
            layout.setPadding(5, 0, 0, 0);

            //Create the checkbox
            CheckBox cb = new CheckBox(this);
            cb.setChecked(usage.selected);
            ViewTagger.setTag(cb, usage);
            cb.setId(R.id.UPDATE_FROM_INTERNET_FIELD_CHECKBOX);
            //add override capability
            cb.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final CheckBox cb = (CheckBox) v;
                    final FieldUsages.FieldUsage usage = (FieldUsages.FieldUsage) ViewTagger.getTag(cb);
                    if (usage != null) {
                        if (!cb.isChecked() && cb.getText().toString().contains(getResources().getString(R.string.usage_copy_if_blank))) {
                            if (usage.canAppend) {
                                setCheckBoxText(cb, usage.stringId, R.string.usage_add_extra);
                                cb.setChecked(true); //reset to checked
                                usage.usage = FieldUsages.Usages.ADD_EXTRA;
                            } else {
                                setCheckBoxText(cb, usage.stringId, R.string.usage_overwrite);
                                cb.setChecked(true); //reset to checked
                                usage.usage = FieldUsages.Usages.OVERWRITE;
                            }
                        } else if (cb.getText().toString().contains(getResources().getString(R.string.usage_add_extra))) {
                            setCheckBoxText(cb, usage.stringId, R.string.usage_overwrite);
                            cb.setChecked(true); //reset to checked
                            usage.usage = FieldUsages.Usages.OVERWRITE;
                        } else if (cb.getText().toString().contains(getResources().getString(R.string.usage_overwrite))) {
                            setCheckBoxText(cb, usage.stringId, R.string.usage_copy_if_blank);
                            usage.usage = FieldUsages.Usages.COPY_IF_BLANK;
                        }
                        ViewTagger.setTag(cb, usage);
                    }
                }

                /** setText as "text (extra)" */
                private void setCheckBoxText(@NonNull final CheckBox cbx, final int textId, final int extraId) {
                    cbx.setText(getResources().getString(R.string.a_bracket_b_bracket,
                            getResources().getString(textId),
                            getResources().getString(extraId)));
                }
            });

            cb.setTextAppearance(this, android.R.style.TextAppearance_Large);
            String extra;
            switch (usage.usage) {
                case ADD_EXTRA:
                    extra = getResources().getString(R.string.usage_add_extra);
                    break;
                case COPY_IF_BLANK:
                    extra = getResources().getString(R.string.usage_copy_if_blank);
                    break;
                case OVERWRITE:
                    extra = getResources().getString(R.string.usage_overwrite);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Usage");
            }
            cb.setText(getResources().getString(R.string.a_bracket_b_bracket,
                    getResources().getString(usage.stringId), extra));
            layout.addView(cb);

            //Add the LinearLayout to the parent
            parent.addView(layout);
        }

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // Get the selections the user made
                if (readUserSelections() == 0) {
                    Toast.makeText(UpdateFromInternet.this, R.string.select_min_1_field, Toast.LENGTH_LONG).show();
                    return;
                }

                // If they have selected thumbnails, check if they want to download ALL.
                boolean thumbnail_check = false;
                try {
                    thumbnail_check = mFieldUsages.get(UniqueId.BKEY_THUMBNAIL).selected;
                } catch (NullPointerException e) {
                    Logger.logError(e);
                }
                if (thumbnail_check) {
                    // Verify - this can be a dangerous operation
                    AlertDialog alertDialog = new AlertDialog.Builder(UpdateFromInternet.this).setMessage(R.string.overwrite_thumbnail).create();
                    alertDialog.setTitle(R.string.update_fields);
                    alertDialog.setIcon(R.drawable.ic_info_outline);
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, UpdateFromInternet.this.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mFieldUsages.get(UniqueId.BKEY_THUMBNAIL).usage = FieldUsages.Usages.OVERWRITE;
                            startUpdate();
                        }
                    });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, UpdateFromInternet.this.getResources().getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        @SuppressWarnings("EmptyMethod")
                        public void onClick(final DialogInterface dialog, final int which) {
                            //do nothing
                        }
                    });
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, UpdateFromInternet.this.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mFieldUsages.get(UniqueId.BKEY_THUMBNAIL).usage = FieldUsages.Usages.COPY_IF_BLANK;
                            startUpdate();
                        }
                    });
                    alertDialog.show();
                } else {
                    startUpdate();
                }
            }
        });

        Button cancelBtn = findViewById(R.id.cancel);
        cancelBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
            }
        });
    }

    private int readUserSelections() {
        LinearLayout parent = findViewById(R.id.manage_fields_scrollview);
        int nChildren = parent.getChildCount();
        int nSelected = 0;
        for (int i = 0; i < nChildren; i++) {
            View v = parent.getChildAt(i);
            CheckBox cb = v.findViewById(R.id.UPDATE_FROM_INTERNET_FIELD_CHECKBOX);
            if (cb != null) {
                FieldUsages.FieldUsage usage = (FieldUsages.FieldUsage) ViewTagger.getTag(cb);
                usage.selected = cb.isChecked();
                if (usage.selected) {
                    nSelected++;
                }
            }
        }
        return nSelected;
    }

    private void startUpdate() {
        UpdateThumbnailsThread t = new UpdateThumbnailsThread(getTaskManager(), mFieldUsages, mThumbnailsHandler);
        mUpdateSenderId = t.getSenderId();
        UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, false);
        t.start();
    }

    @Override
    protected void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        if (mUpdateSenderId != 0) {
            UpdateThumbnailsThread.getMessageSwitch().removeListener(mUpdateSenderId, mThumbnailsHandler);
        }
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, true);
        }
        Tracker.exitOnResume(this);
    }
}

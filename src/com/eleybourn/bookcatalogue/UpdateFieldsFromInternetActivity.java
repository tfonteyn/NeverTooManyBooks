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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * NEWKIND must stay in sync with {@link UpdateFieldsFromInternetTask}
 *
 * FIXME ... re-test and see why the progress stops. Seems we hit some limit in number of HTTP connections (server imposed ?)
 */
public class UpdateFieldsFromInternetActivity extends BaseActivityWithTasks {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_UPDATE_FROM_INTERNET;

    /** optionally limit the sites to search on. By default uses {@link SearchSites.Site#SEARCH_ALL} */
    private static final String REQUEST_BKEY_SEARCH_SITES = "SearchSites";
    /** which fields to update and how */
    private final FieldUsages mFieldUsages = new FieldUsages();
    /** where to look */
    private int mSearchSites = SearchSites.Site.SEARCH_ALL;
    /** 0 for all books, or a specific book */
    private long mBookId = 0;

    private ViewGroup mListContainer;

    /** senderId of the update task */
    private long mUpdateSenderId = 0;

    /** this is where the results can be 'consumed' before finishing this activity */
    private final ManagedTask.ManagedTaskListener mSearchTaskListener = new ManagedTask.ManagedTaskListener() {
        @Override
        public void onTaskFinished(final @NonNull ManagedTask task) {
            mUpdateSenderId = 0;
            Intent data = new Intent();
            data.putExtra(UniqueId.BKEY_CANCELED, task.isCancelled());

            // 0 if we did 'all books' or the id of the (hopefully) updated book.
            data.putExtra(UniqueId.KEY_ID, mBookId);
            if (mBookId == 0) {
                // task cancelled does not mean that nothing was done. Books *will* be updated until the cancelling happened
                setResult(Activity.RESULT_OK, data); /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
            } else {
                // but if a single book was cancelled, flag that up
                setResult(Activity.RESULT_CANCELED, data); /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
            }
            finish();
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_update_from_internet;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mBookId = extras.getLong(UniqueId.KEY_ID, 0L);
            if (mBookId > 0) {
                // we're only requesting ONE book to be updated.
                TextView authorView = findViewById(R.id.author);
                authorView.setText(extras.getString(UniqueId.KEY_AUTHOR_FORMATTED));
                TextView titleView = findViewById(R.id.title);
                titleView.setText(extras.getString(UniqueId.KEY_TITLE));

                findViewById(R.id.row_book).setVisibility(View.VISIBLE);
            }

            mSearchSites = extras.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.Site.SEARCH_ALL);
        }

        setTitle(R.string.lbl_update_fields_to_update);

        if ((mSearchSites & SearchSites.Site.SEARCH_LIBRARY_THING) != 0) {
            LibraryThingManager.showLtAlertIfNecessary(this, false, "update_from_internet");
        }

        mListContainer = findViewById(R.id.manage_fields_scrollview);

        initFields();
        populateFields();
        initCancelConfirmButtons();
        Tracker.exitOnCreate(this);
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, UniqueId.KEY_AUTHOR_ID,
                R.string.lbl_author, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(UniqueId.KEY_TITLE,
                R.string.lbl_title, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_ISBN,
                R.string.lbl_isbn, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_HAVE_THUMBNAIL,
                R.string.lbl_cover, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, UniqueId.KEY_SERIES_NAME,
                R.string.lbl_series, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(UniqueId.BKEY_TOC_TITLES_ARRAY, UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                R.string.table_of_content, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(UniqueId.KEY_BOOK_PUBLISHER,
                R.string.lbl_publisher, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED,
                R.string.lbl_date_published, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_FIRST_PUBLICATION,
                R.string.lbl_first_publication, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_DESCRIPTION,
                R.string.lbl_description, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_PAGES,
                R.string.lbl_pages, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_PRICE_LISTED,
                R.string.lbl_price_listed, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_FORMAT,
                R.string.lbl_format, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_GENRE,
                R.string.lbl_genre, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.KEY_BOOK_LANGUAGE,
                R.string.lbl_language, Fields.FieldUsage.Usage.CopyIfBlank, false);
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages + check for visibility
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(final @NonNull String fieldId,
                              final @StringRes int nameStringId,
                              final @NonNull Fields.FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (Fields.isVisible(fieldId)) {
            mFieldUsages.put(new Fields.FieldUsage(fieldId, nameStringId, defaultUsage, isList));
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages
     * @param visField     Field name to check for visibility.
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(final @NonNull String fieldId,
                              final @NonNull String visField,
                              final @StringRes int nameStringId,
                              final @NonNull Fields.FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (Fields.isVisible(visField)) {
            mFieldUsages.put(new Fields.FieldUsage(fieldId, nameStringId, defaultUsage, isList));
        }
    }

    /**
     * Display the list of fields, dynamically adding them in a loop
     */
    private void populateFields() {

        for (Fields.FieldUsage usage : mFieldUsages.values()) {
            View row = this.getLayoutInflater().inflate(R.layout.row_update_from_internet, mListContainer, false);

            TextView fieldLabel = row.findViewById(R.id.field);
            fieldLabel.setText(usage.getLabel(this));

            CompoundButton cb = row.findViewById(R.id.usage);
            cb.setChecked(usage.isSelected());
            cb.setText(usage.getUsageInfo(UpdateFieldsFromInternetActivity.this));
            cb.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // ENHANCE The check is really a FOUR-state.
                    final CompoundButton cb = (CompoundButton) v;
                    final Fields.FieldUsage usage = ViewTagger.getTagOrThrow(cb);
                    usage.nextState();
                    cb.setChecked(usage.isSelected());
                    cb.setText(usage.getUsageInfo(UpdateFieldsFromInternetActivity.this));
                }
            });

            ViewTagger.setTag(cb, usage);
            mListContainer.addView(row);
        }
    }

    private void initCancelConfirmButtons() {
        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // sanity check
                if (countUserSelections() == 0) {
                    StandardDialogs.showUserMessage(UpdateFieldsFromInternetActivity.this, R.string.warning_select_min_1_field);
                    return;
                }

                // If they have selected thumbnails, check if they want to download ALL
                Fields.FieldUsage coversWanted = mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL);
                // but don't ask if its a single book only; just download it.
                if (mBookId == 0 && coversWanted.isSelected()) {
                    // Verify - this can be a dangerous operation
                    AlertDialog dialog = new AlertDialog.Builder(UpdateFieldsFromInternetActivity.this)
                            .setMessage(R.string.overwrite_thumbnail)
                            .setTitle(R.string.lbl_update_fields)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .create();
                    dialog.setButton(AlertDialog.BUTTON_POSITIVE, UpdateFieldsFromInternetActivity.this.getString(R.string.yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int which) {
                                    mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL).usage = Fields.FieldUsage.Usage.Overwrite;
                                    startUpdate(mBookId);
                                }
                            });
                    dialog.setButton(AlertDialog.BUTTON_NEGATIVE, UpdateFieldsFromInternetActivity.this.getString(android.R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @SuppressWarnings("EmptyMethod")
                                public void onClick(final DialogInterface dialog, final int which) {
                                    //do nothing
                                }
                            });
                    dialog.setButton(AlertDialog.BUTTON_NEUTRAL, UpdateFieldsFromInternetActivity.this.getString(R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int which) {
                                    mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL).usage = Fields.FieldUsage.Usage.CopyIfBlank;
                                    startUpdate(mBookId);
                                }
                            });
                    dialog.show();
                } else {
                    startUpdate(mBookId);
                }
            }
        });

        // don't start update, just quit.
        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @return super.onCreateOptionsMenu(menu);
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(final @NonNull Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.tab_lbl_search_sites)
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(this, SearchAdminActivity.class);
                intent.putExtra(SearchAdminActivity.REQUEST_BKEY_TAB, SearchAdminActivity.TAB_SEARCH_ORDER);
                startActivityForResult(intent, SearchAdminActivity.REQUEST_CODE); /* 4266b81b-137b-4647-aa1c-8ec0fc8726e6 */
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case SearchAdminActivity.REQUEST_CODE: /* 4266b81b-137b-4647-aa1c-8ec0fc8726e6 */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES, mSearchSites);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    private int countUserSelections() {
        int nChildren = mListContainer.getChildCount();
        int nSelected = 0;
        for (int i = 0; i < nChildren; i++) {
            View v = mListContainer.getChildAt(i);
            CompoundButton cb = v.findViewById(R.id.usage);
            if (cb != null) {
                Fields.FieldUsage usage = ViewTagger.getTagOrThrow(cb);
                if (usage.isSelected()) {
                    nSelected++;
                }
            }
        }
        return nSelected;
    }

    /**
     * @param bookId 0 for all books, or a valid book id for one book
     */
    private void startUpdate(final long bookId) {
        UpdateFieldsFromInternetTask updateTask = new UpdateFieldsFromInternetTask(getTaskManager(),
                mSearchSites, mFieldUsages, mSearchTaskListener);

        if (bookId > 0) {
            updateTask.setBookId(bookId);
        }

        mUpdateSenderId = updateTask.getSenderId();
        UpdateFieldsFromInternetTask.getMessageSwitch().addListener(mUpdateSenderId, mSearchTaskListener, false);
        updateTask.start();
    }

    @Override
    @CallSuper
    protected void onPause() {
        Tracker.enterOnPause(this);
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.getMessageSwitch().removeListener(mUpdateSenderId, mSearchTaskListener);
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.getMessageSwitch().addListener(mUpdateSenderId, mSearchTaskListener, true);
        }
        Tracker.exitOnResume(this);
    }

    /**
     * Class to manage a collection of fields and the rules for importing them.
     * Inherits from {@link LinkedHashMap} to guarantee iteration order.
     *
     * @author Philip Warner
     */
    public static class FieldUsages extends LinkedHashMap<String, Fields.FieldUsage> {
        private static final long serialVersionUID = 1L;

        public void put(final @NonNull Fields.FieldUsage usage) {
            this.put(usage.fieldId, usage);
        }
    }
}

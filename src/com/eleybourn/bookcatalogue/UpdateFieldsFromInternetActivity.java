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
import android.content.Context;
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
    /** */
    private final FieldUsages mFieldUsages = new FieldUsages();
    private int mSearchSites = SearchSites.Site.SEARCH_ALL;
    private long mBookId = 0;

    private ViewGroup mListContainer;

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

    private void initFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, UniqueId.KEY_AUTHOR_ID,
                R.string.lbl_author, true, FieldUsage.Usage.AddExtra);
        addIfVisible(UniqueId.KEY_TITLE,
                R.string.lbl_title, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_ISBN,
                R.string.lbl_isbn, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.BKEY_HAVE_THUMBNAIL,
                R.string.lbl_cover, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, UniqueId.KEY_SERIES_NAME,
                R.string.lbl_series, true, FieldUsage.Usage.AddExtra);
        addIfVisible(UniqueId.BKEY_TOC_TITLES_ARRAY, UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                R.string.table_of_content, true, FieldUsage.Usage.AddExtra);
        addIfVisible(UniqueId.KEY_BOOK_PUBLISHER,
                R.string.lbl_publisher, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED,
                R.string.lbl_date_published, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_FIRST_PUBLICATION,
                R.string.lbl_first_publication, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_DESCRIPTION,
                R.string.lbl_description, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_PAGES,
                R.string.lbl_pages, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_PRICE_LISTED,
                R.string.lbl_price_listed, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_FORMAT,
                R.string.lbl_format, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_GENRE,
                R.string.lbl_genre, false, FieldUsage.Usage.CopyIfBlank);
        addIfVisible(UniqueId.KEY_BOOK_LANGUAGE,
                R.string.lbl_language, false, FieldUsage.Usage.CopyIfBlank);
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param field     name to use in FieldUsages + check for visibility
     * @param stringId  of field label string
     * @param canAppend if the field is a list to which we can append to
     * @param usage     Usage to apply.
     */
    private void addIfVisible(final @NonNull String field,
                              final @StringRes int stringId,
                              final boolean canAppend,
                              final @NonNull FieldUsage.Usage usage) {

        if (Fields.isVisible(field)) {
            mFieldUsages.put(new FieldUsage(field, stringId, usage, canAppend));
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param field     name to use in FieldUsages
     * @param visField  Field name to check for visibility.
     * @param stringId  of field label string
     * @param canAppend if the field is a list to which we can append to
     * @param usage     Usage to apply.
     */
    private void addIfVisible(final @NonNull String field,
                              final @NonNull String visField,
                              final @StringRes int stringId,
                              final boolean canAppend,
                              final @NonNull FieldUsage.Usage usage) {

        if (Fields.isVisible(visField)) {
            mFieldUsages.put(new FieldUsage(field, stringId, usage, canAppend));
        }
    }

    /**
     * Display the list of fields, dynamically adding them in a loop
     */
    private void populateFields() {

        for (FieldUsage usage : mFieldUsages.values()) {
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
                    final FieldUsage usage = ViewTagger.getTagOrThrow(cb);
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
                FieldUsage coversWanted = mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL);
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
                                    mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL).usage = FieldUsage.Usage.Overwrite;
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
                                    mFieldUsages.get(UniqueId.BKEY_HAVE_THUMBNAIL).usage = FieldUsage.Usage.CopyIfBlank;
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
                FieldUsage usage = ViewTagger.getTagOrThrow(cb);
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
    public static class FieldUsages extends LinkedHashMap<String, FieldUsage> {
        private static final long serialVersionUID = 1L;

        public void put(final @NonNull FieldUsage usage) {
            this.put(usage.key, usage);
        }
    }

    public static class FieldUsage {
        /** a key, usually from {@link com.eleybourn.bookcatalogue.UniqueId} */
        @NonNull
        public final String key;
        /** is the field a list type */
        private final boolean canAppend;
        /** label to show to the user */
        @StringRes
        private final int labelId;
        /** how to use this field */
        @NonNull
        public Usage usage;


        public FieldUsage(final @NonNull String name,
                          final @StringRes int id,
                          final @NonNull Usage usage,
                          final boolean canAppend) {
            this.key = name;
            this.labelId = id;
            this.canAppend = canAppend;
            this.usage = usage;
        }

        public boolean isSelected() {
            return (usage != Usage.Skip);
        }

        public String getLabel(final @NonNull Context context) {
            return context.getString(labelId);
        }

        public String getUsageInfo(final @NonNull Context context) {
            return context.getString(usage.getStringId());
        }

        /**
         * Cycle to the next Usage stage:
         *
         * if (canAppend): Skip -> CopyIfBlank -> AddExtra -> Overwrite -> Skip
         * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
         */
        public void nextState() {
            switch (usage) {
                case Skip:
                    usage = Usage.CopyIfBlank;
                    break;
                case CopyIfBlank:
                    if (canAppend) {
                        usage = Usage.AddExtra;
                    } else {
                        usage = Usage.Overwrite;
                    }
                    break;
                case AddExtra:
                    usage = Usage.Overwrite;
                    break;
                case Overwrite:
                    usage = Usage.Skip;
            }
        }

        public enum Usage {
            Skip, CopyIfBlank, AddExtra, Overwrite;

            @StringRes
            int getStringId() {
                switch (this) {
                    case CopyIfBlank:
                        return R.string.lbl_field_usage_copy_if_blank;
                    case AddExtra:
                        return R.string.lbl_field_usage_add_extra;
                    case Overwrite:
                        return R.string.lbl_field_usage_overwrite;
                    default:
                        return R.string.usage_skip;
                }
            }
        }
    }
}

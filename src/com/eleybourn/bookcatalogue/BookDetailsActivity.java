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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A tab host activity which holds the edit book tabs
 * 1. Edit Details / Show Details
 * 2. Edit Comments  / Show Comments
 * 3. Loan Book
 * 4. Anthology titles
 *
 * @author Evan Leybourn
 */
public class BookDetailsActivity extends BookCatalogueActivity
        implements EditBookAbstractFragment.BookEditManager,
        OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    /**
     * Tabs in order, see {@link #mTabClasses}
     */
    public static final String TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_FRIENDS = 2;
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    private static final String FLATTENED_BOOKLIST_POSITION = "FlattenedBooklistPosition";
    private static final String BKEY_FLATTENED_BOOKLIST = "FlattenedBooklist";
    /**
     * Key using in intent to start this class in read-only mode
     */
    private static final String LOCAL_BKEY_READ_ONLY = "key_read_only";
    /**
     * Classes used for the Tabs (in order)
     */
    private static final Class[] mTabClasses = {
            EditBookFieldsFragment.class,
            EditBookNotesFragment.class,
            EditBookLoanedFragment.class,
            EditBookAnthologyFragment.class
    };
    private final CatalogueDBAdapter mDb = new CatalogueDBAdapter(this);
    private FlattenedBooklist mList = null;
    private GestureDetector mGestureDetector;
    private boolean mIsDirtyFlg = false;
    private long mRowId;
    private BookData mBookData;
    private boolean mIsReadOnly;
    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mList == null) {
                return false;
            }

            // Make sure we have considerably more X-velocity than Y-velocity;
            // otherwise it might be a scroll.
            if (Math.abs(velocityX / velocityY) > 2) {
                boolean moved;
                // Work out which way to move, and do it.
                if (velocityX > 0) {
                    moved = mList.movePrev();
                } else {
                    moved = mList.moveNext();
                }
                if (moved) {
                    setRowId(mList.getBookId());
                }
                return true;
            } else {
                return false;
            }
        }
    };

    private TabLayout mTabLayout;
    private TabLayout.Tab mAnthologyTab;

    /** Lists in database so far, we cache them for performance */
    private List<String> mFormats;
    private List<String> mGenres;
    private List<String> mLanguages;
    private List<String> mLocations;
    private List<String> mPublishers;

    /**
     * Open book for viewing in edit or read-only mode.
     *
     * @param activity Current activity from which we start
     * @param id       The id of the book to view
     * @param builder  (Optional) builder for underlying book list. Only used in read-only view.
     * @param position (Optional) position in underlying book list. Only used in read-only view.
     */
    public static void openBook(@NonNull final Activity activity,
                                final long id,
                                @Nullable final BooklistBuilder builder,
                                @Nullable final Integer position) {
        if (BCPreferences.getOpenBookReadOnly()) {
            // Make a flattened copy of the list of books, if available
            String listTable = null;
            if (builder != null) {
                listTable = builder.createFlattenedBooklist().getTable().getName();
            }
            startReadOnlyMode(activity, id, listTable, position);
        } else {
            startEditMode(activity, id, BookDetailsActivity.TAB_EDIT);
        }
    }

    /**
     * Load in edit mode based on the provided id. Also open to the provided tab.
     *
     * @param id  The id of the book to edit
     * @param tab Which tab to open first
     */
    public static void startEditMode(@NonNull final Activity activity,
                                     final long id,
                                     final int tab) {
        Intent intent = new Intent(activity, BookDetailsActivity.class);
        intent.putExtra(UniqueId.KEY_ID, id);
        intent.putExtra(BookDetailsActivity.TAB, tab);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_EDIT_BOOK);
    }

    /**
     * Load in read-only mode. The first tab is book details.
     *
     * @param activity  Current activity from which we start
     * @param id        The id of the book to view
     * @param listTable (Optional) name of the temp table containing a list of book IDs.
     * @param position  (Optional) position in underlying book list. Only used in read-only view.
     */
    private static void startReadOnlyMode(@NonNull final Activity activity,
                                          final long id,
                                          @Nullable final String listTable,
                                          @Nullable final Integer position) {
        Intent intent = new Intent(activity, BookDetailsActivity.class);
        intent.putExtra(BKEY_FLATTENED_BOOKLIST, listTable);
        if (position != null) {
            intent.putExtra(FLATTENED_BOOKLIST_POSITION, position);
        }
        intent.putExtra(UniqueId.KEY_ID, id);
        intent.putExtra(BookDetailsActivity.TAB, BookDetailsActivity.TAB_EDIT); // needed extra for creating BookDetailsActivity
        intent.putExtra(BookDetailsActivity.LOCAL_BKEY_READ_ONLY, true);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_VIEW_BOOK);
    }


    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_book_base;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);

        super.onCreate(savedInstanceState);
        mDb.open();

        // Get the extras; we use them a lot
        Bundle extras = getIntent().getExtras();

        // We need the row ID
        long rowId = 0;
        if (savedInstanceState != null) {
            rowId = savedInstanceState.getLong(UniqueId.KEY_ID);
        }

        if ((rowId == 0) && (extras != null)) {
            rowId = extras.getLong(UniqueId.KEY_ID);
        }
        mRowId = rowId;

        boolean isExistingBook = (mRowId > 0);

        mIsReadOnly = (extras != null) && isExistingBook
                && extras.getBoolean(LOCAL_BKEY_READ_ONLY, false);

        // prepare the tabs, will use multiple for editing, or invisible one for viewing
        mTabLayout = findViewById(R.id.tabpanel);
        mTabLayout.addOnTabSelectedListener(new TabListener());

        initBookData(mRowId, savedInstanceState == null ? extras : savedInstanceState);

        if (mIsReadOnly) {
            initForReadOnly(extras);
        } else {
            initForEditing(extras, isExistingBook);
        }

        initCancelConfirmButtons(isExistingBook);
        initBooklist(extras, savedInstanceState);

        // Must come after all book data and list retrieved.
        initActivityTitle();

        Tracker.exitOnCreate(this);
    }


    /**
     * initial setup for read-only viewing
     */
    private void initForReadOnly(@Nullable final Bundle extras) {
        BookDetailsFragment details = new BookDetailsFragment();
        details.setArguments(extras);
        replaceTab(details);

        mTabLayout.setVisibility(View.GONE);
        findViewById(R.id.buttonbar_cancel_save).setVisibility(View.GONE);
    }

    /**
     * initial setup for editing
     */
    private void initForEditing(@Nullable final Bundle extras,
                                final boolean isExistingBook) {
        ArrayList<TabLayout.Tab> mAllTabs = new ArrayList<>();
        try {

            TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.details).setTag(mTabClasses[TAB_EDIT].newInstance());
            mTabLayout.addTab(tab);
            mAllTabs.add(tab);

            tab = mTabLayout.newTab().setText(R.string.notes).setTag(mTabClasses[TAB_EDIT_NOTES].newInstance());
            mTabLayout.addTab(tab);
            mAllTabs.add(tab);

            if (isExistingBook) {
                tab = mTabLayout.newTab().setText(R.string.loan).setTag(mTabClasses[TAB_EDIT_FRIENDS].newInstance());
                mTabLayout.addTab(tab);
                mAllTabs.add(tab);

                boolean isAnthology = (mBookData.getRowId() > 0) && (mBookData.getInt(BookData.KEY_IS_ANTHOLOGY) != 0);
                showAnthologyTab(isAnthology);
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Creating BookDetailsActivity tabs failed?");
        }


        if (extras != null && extras.containsKey(TAB)) {
            int i = extras.getInt(TAB);
            if (mAllTabs.size() > i) {
                mAllTabs.get(i).select();
                //replaceTab(mAllTabs.get(i));
            }
        } else {
            mAllTabs.get(TAB_EDIT).select();
            //replaceTab(mAllTabs.get(TAB_EDIT));
        }
        mTabLayout.setVisibility(View.VISIBLE);
        findViewById(R.id.buttonbar_cancel_save).setVisibility(View.VISIBLE);
    }

    private void initCancelConfirmButtons(final boolean isExistingBook) {
        Button mConfirmButton = findViewById(R.id.confirm);
        if (isExistingBook) {
            mConfirmButton.setText(R.string.confirm_save);
        } else {
            mConfirmButton.setText(R.string.confirm_add);
        }

        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState(new DoConfirmAction());
            }
        });

        Button mCancelButton = findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Cleanup because we may have made global changes TODO: detect this if actually needed
                mDb.purgeAuthors();
                mDb.purgeSeries();
                // We're done.
                setResult(Activity.RESULT_OK);

                if (isDirty()) {
                    StandardDialogs.showConfirmUnsavedEditsDialog(BookDetailsActivity.this, null);
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * This function will populate the forms elements in three different ways
     *
     * 1. If a valid rowId exists it will populate the fields from the database
     *
     * 2. If fields have been passed from another activity (e.g. {@link BookISBNSearchActivity}) it
     * will populate the fields from the bundle
     *
     * 3. It will leave the fields blank for new books.
     */
    private void initBookData(final long rowId, @Nullable final Bundle bestBundle) {
        if (bestBundle != null && bestBundle.containsKey(UniqueId.BKEY_BOOK_DATA)) {
            // If we have saved book data, use it
            mBookData = new BookData(rowId, bestBundle.getBundle(UniqueId.BKEY_BOOK_DATA));
        } else {
            // Just load based on rowId
            mBookData = new BookData(rowId);
        }
    }

    /**
     * If we are passed a flat book list, get it and validate it
     */
    private void initBooklist(@Nullable final Bundle extras,
                              @Nullable final Bundle savedInstanceState) {
        if (extras != null) {
            String list = extras.getString(BKEY_FLATTENED_BOOKLIST);
            if (list != null && !list.isEmpty()) {
                mList = new FlattenedBooklist(mDb, list);
                // Check to see it really exists. The underlying table disappeared once in testing
                // which is hard to explain; it theoretically should only happen if the app closes
                // the database or if the activity pauses with 'isFinishing()' returning true.
                if (mList.exists()) {
                    int pos;
                    if (savedInstanceState != null && savedInstanceState.containsKey(FLATTENED_BOOKLIST_POSITION)) {
                        pos = savedInstanceState.getInt(FLATTENED_BOOKLIST_POSITION);
                    } else if (extras.containsKey(FLATTENED_BOOKLIST_POSITION)) {
                        pos = extras.getInt(FLATTENED_BOOKLIST_POSITION);
                    } else {
                        pos = 0;
                    }
                    mList.moveTo(pos);
                    while (mList.getBookId() != mRowId) {
                        if (!mList.moveNext()) {
                            break;
                        }
                    }
                    if (mList.getBookId() != mRowId) {
                        mList.close();
                        mList = null;
                    } else {
                        // Add a gesture lister for 'swipe' gestures
                        mGestureDetector = new GestureDetector(this, mGestureListener);
                    }

                } else {
                    mList.close();
                    mList = null;
                }
            }
        }
    }

    /**
     * Sets title of the parent activity depending on show/edit/add
     */
    private void initActivityTitle() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            if (mIsReadOnly && mList != null) {
                // display a book
//   to long, doesn't fit
//                bar.setTitle(mBookData.getString(KEY_TITLE));
//                bar.setSubtitle(mBookData.getAuthorTextShort()
//                                + String.format(" (" + getResources().getString(R.string.x_of_y) + ")",
//                                  mList.getAbsolutePosition(), mList.getCount())
//                );
                bar.setTitle(this.getResources().getString(R.string.book_details));
                bar.setSubtitle(null);

            } else if (mBookData.getRowId() > 0) {
                // editing an existing book
                bar.setTitle(mBookData.getString(UniqueId.KEY_TITLE));
                bar.setSubtitle(mBookData.getAuthorTextShort());
            } else {
                // new book
                bar.setTitle(this.getResources().getString(R.string.menu_insert));
                bar.setSubtitle(null);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * If 'back' is pressed, and the user has made changes, ask them if they
     * really want to lose the changes.
     *
     * We don't use onBackPressed because it does not work with API level 4.
     */
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isDirty()) {
                StandardDialogs.showConfirmUnsavedEditsDialog(this, null);
            } else {
                doFinish();
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * We override the dispatcher because the ScrollView will consume
     * all events otherwise.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        super.dispatchTouchEvent(event);
        // Always return true; we want the events.
        return true;
    }

    @Override
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDb.close();
        Tracker.exitOnDestroy(this);
    }

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     *
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereference'd, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
     */
    @Override
    public void onPause() {
        if (mList != null) {
            mList.close();
            if (this.isFinishing()) {
                mList.deleteData();
            }
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);
        super.onSaveInstanceState(outState);

        outState.putLong(UniqueId.KEY_ID, mRowId);
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, mBookData.getRawData());
        if (mList != null) {
            outState.putInt(FLATTENED_BOOKLIST_POSITION, (int) mList.getPosition());
        }
        outState.putInt(BookDetailsActivity.TAB, mTabLayout.getSelectedTabPosition());
        Tracker.exitOnSaveInstanceState(this);
    }

    /**
     * Check if edits need saving, and finish the activity if not
     */
    private void doFinish() {
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this, new Runnable() {
                @Override
                public void run() {
                    finishAndSendIntent();
                }
            });
        } else {
            finishAndSendIntent();
        }
    }
    /**
     * Actually finish this activity making sure an intent is returned.
     */
    private void finishAndSendIntent() {
        Intent intent = new Intent();
        intent.putExtra(UniqueId.KEY_ID, mBookData.getRowId());
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * menu handler; handle the 'home' key, otherwise, pass on the event
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                doFinish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onPartialDatePickerSet(final int dialogId,
                                       @NonNull final PartialDatePickerFragment dialog,
                                       @Nullable final Integer year,
                                       @Nullable final Integer month,
                                       @Nullable final Integer day) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnPartialDatePickerListener) {
            ((OnPartialDatePickerListener) frag).onPartialDatePickerSet(dialogId, dialog, year, month, day);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received date dialog result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onPartialDatePickerCancel(final int dialogId,
                                          @NonNull final PartialDatePickerFragment dialog) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnPartialDatePickerListener) {
            ((OnPartialDatePickerListener) frag).onPartialDatePickerCancel(dialogId, dialog);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received date dialog cancellation with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }

    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onTextFieldEditorSave(final int dialogId,
                                      @NonNull final TextFieldEditorFragment dialog,
                                      @NonNull final String newText) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnTextFieldEditorListener) {
            ((OnTextFieldEditorListener) frag).onTextFieldEditorSave(dialogId, dialog, newText);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onTextFieldEditorSave result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onTextFieldEditorCancel(final int dialogId,
                                        @NonNull final TextFieldEditorFragment dialog) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnTextFieldEditorListener) {
            ((OnTextFieldEditorListener) frag).onTextFieldEditorCancel(dialogId, dialog);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onTextFieldEditorCancel result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onBookshelfCheckChanged(@NonNull final String textList,
                                        @NonNull final String encodedList) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnBookshelfCheckChangeListener) {
            ((OnBookshelfCheckChangeListener) frag).onBookshelfCheckChanged(textList, encodedList);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onBookshelfCheckChanged result with no fragment to handle it"));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the current status of the data in this activity
     */
    @Override
    public boolean isDirty() {
        return mIsDirtyFlg;
    }

    /**
     * Mark the data as dirty (or not)
     */
    @Override
    public void setDirty(final boolean dirty) {
        mIsDirtyFlg = dirty;
    }

    @Override
    public BookData getBookData() {
        return mBookData;
    }

    @Override
    public void setRowId(final long id) {
        if (mRowId != id) {
            mRowId = id;
            initBookData(id, null);
            Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
            if (frag instanceof DataEditor) {
                ((DataEditor) frag).reloadData(mBookData);
            }
            initActivityTitle();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Load a publisher list; reloading this list every time a tab changes is
     * slow. So we cache it.
     *
     * @return List of publishers
     */
    @Override
    @NonNull
    public List<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = mDb.getPublishers();
        }
        return mPublishers;
    }

    /**
     * Load a genre list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of genres
     */
    @Override
    @NonNull
    public List<String> getGenres() {
        if (mGenres == null) {
            mGenres = mDb.getGenres();
        }
        return mGenres;
    }

    /**
     * Load a location list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of locations
     */
    @Override
    @NonNull
    public List<String> getLocations() {
        if (mLocations == null) {
            mLocations = mDb.getLocations();
        }
        return mLocations;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages
     */
    @Override
    @NonNull
    public List<String> getLanguages() {
        if (mLanguages == null) {
            mLanguages = mDb.getLanguages();
        }
        return mLanguages;
    }

    /**
     * Load a format list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of formats
     */
    @Override
    @NonNull
    public List<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void replaceTab(@NonNull final Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    /**
     * Show or hide the anthology tab
     * FIXME:  android:ellipsize="end" and maxLine="1" on the TextView used by the mAnthologyTab... how ?
     */
    public void showAnthologyTab(final boolean showAnthology) {
        if (showAnthology) {
            if (mAnthologyTab == null) {
                try {
                    mAnthologyTab = mTabLayout.newTab()
                            .setText(R.string.anthology)
                            .setTag(mTabClasses[TAB_EDIT_ANTHOLOGY].newInstance());
                } catch (InstantiationException | IllegalAccessException ignore) {
                }
            }
            mTabLayout.addTab(mAnthologyTab);
        } else {
            if (mAnthologyTab != null) {
                mTabLayout.removeTab(mAnthologyTab);
            }
            mAnthologyTab = null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate the current data in all fields that have validators. Display any errors.
     */
    private void validate() {
        if (!mBookData.validate()) {
            Toast.makeText(this, mBookData.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * This will save a book into the database, by either updating or created a
     * book. Minor modifications will be made to the strings:
     * <ul>
     * <li>strings will be .trim()'d
     * <li>Titles will be reworded so 'a', 'the', 'an' will be moved to the end of the string (only for NEW books)
     * <li>Date published will be converted from a date to a string
     * <li>Thumbnails will also be saved to the correct location
     * <li>It will check if the book already exists (isbn search) if you are creating a book; if so the user will be prompted to confirm.
     * </ul>
     * In all cases, once the book is added/created, or not, the appropriate method of the
     * passed nextStep parameter will be executed. Passing nextStep is necessary because
     * this method may return after displaying a dialogue.
     *
     * @param nextStep The next step to be executed on success/failure.
     */
    private void saveState(@NonNull final PostSaveAction nextStep) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof DataEditor) {
            ((DataEditor) frag).saveAllEdits(mBookData);
        }

        // Ignore validation failures; we still validate to get the current values.
        validate();

        // However, there is some data that we really do require...
        if (mBookData.getAuthors().size() == 0) {
            Toast.makeText(this, getResources().getText(R.string.author_required), Toast.LENGTH_LONG).show();
            return;
        }
        if (!mBookData.containsKey(UniqueId.KEY_TITLE)
                || mBookData.getString(UniqueId.KEY_TITLE).trim().isEmpty()) {
            Toast.makeText(this, getResources().getText(R.string.title_required), Toast.LENGTH_LONG).show();
            return;
        }

        if (mRowId == 0) {
            String isbn = mBookData.getString(UniqueId.KEY_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && (mDb.isbnExists(isbn, true))) {
                /*
                 * If it exists, show a dialog and use it to perform the
                 * next action, according to the users choice.
                 */
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(getResources().getString(R.string.duplicate_book_message))
                        .setTitle(R.string.duplicate_book_title)
                        .setIcon(BookCatalogueApp.getAttr(R.attr.ic_content_copy))
                        .create();

                dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        this.getResources().getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                updateOrInsert();
                                nextStep.success();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        this.getResources().getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                nextStep.failure();
                            }
                        });
                dialog.show();
                return;
            }
        }

        // No special actions required...just do it.
        updateOrInsert();
        nextStep.success();
    }

    /**
     * Save the collected book details
     */
    private void updateOrInsert() {
        if (mRowId == 0) {
            long id = mDb.insertBook(mBookData, 0);

            if (id > 0) {
                setRowId(id);
                File thumb = StorageUtils.getTempCoverFile();
                File real = StorageUtils.getCoverFile(mDb.getBookUuid(mRowId));
                StorageUtils.renameFile(thumb, real);
            }
        } else {
            mDb.updateBook(mRowId, mBookData, 0);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public interface PostSaveAction {
        void success();

        @SuppressWarnings("EmptyMethod")
        void failure();
    }

    private class TabListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(final TabLayout.Tab tab) {
            replaceTab((Fragment) tab.getTag()); //TOMF: fragment sits in tag... rethink ?
        }

        @Override
        public void onTabUnselected(final TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(final TabLayout.Tab tab) {
        }
    }

    private class DoConfirmAction implements PostSaveAction {

        DoConfirmAction() {
        }

        public void success() {
            Intent intent = new Intent();
            intent.putExtra(UniqueId.KEY_ID, mBookData.getRowId());
            setResult(Activity.RESULT_OK, intent);
            finish();
        }

        public void failure() {
            // Do nothing
        }
    }
}

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
import android.database.Cursor;
import android.os.Bundle;
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
import com.eleybourn.bookcatalogue.database.ColumnInfo;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * A tab host activity which holds the edit book tabs
 * 1. Edit Details / Show Details
 * 2. Edit Comments  / Show Comments
 * 3. Loan Book
 * 4. Anthology titles
 *
 * @author Evan Leybourn
 */
public class BookEdit extends BookCatalogueActivity implements BookEditFragmentAbstract.BookEditManager,
        OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    public static final String BOOK_DATA = "bookData";
    public static final String FLATTENED_BOOKLIST_POSITION = "FlattenedBooklistPosition";
    public static final String BKEY_FLATTENED_BOOKLIST = "FlattenedBooklist";
    /**
     * Tabs in order, see {@link #mTabClasses}
     */
    public static final String TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_FRIENDS = 2;
    public static final int TAB_EDIT_ANTHOLOGY = 3;
    public static final String ADDED_HAS_INFO = "ADDED_HAS_INFO";
    public static final String ADDED_GENRE = "ADDED_GENRE";
    public static final String ADDED_SERIES = "ADDED_SERIES";
    public static final String ADDED_TITLE = "ADDED_TITLE";
    public static final String ADDED_AUTHOR = "ADDED_AUTHOR";
    /**
     * Key using in intent to start this class in read-only mode
     */
    private static final String KEY_READ_ONLY = "key_read_only";
    /**
     * Classes used for the Tabs (in order)
     */
    private static final Class[] mTabClasses = {
            BookEditFields.class,
            BookEditNotes.class,
            BookEditLoaned.class,
            BookEditAnthology.class
    };
    private final CatalogueDBAdapter mDb = new CatalogueDBAdapter(this);
    private FlattenedBooklist mList = null;
    private GestureDetector mGestureDetector;
    private boolean mIsDirtyFlg = false;
    private String added_genre = "";
    private String added_series = "";
    private String added_title = "";
    private String added_author = "";
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
            if (mList == null)
                return false;

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
    private ArrayList<String> mPublishers;
    private ArrayList<String> mGenres;
    /**
     * List of languages , formats in database so far
     */
    private ArrayList<String> mLanguages;
    private ArrayList<String> mFormats;

    /**
     * @see #openBook(Activity, long, BooklistBuilder, Integer)
     */
    public static void openBook(Activity a, long id) {
        openBook(a, id, null, null);
    }

    /**
     * Open book for viewing in edit or read-only mode.
     *
     * @param a        current activity from which we start
     * @param id       The id of the book to view
     * @param builder  (Optional) builder for underlying book list. Only used in
     *                 read-only view.
     * @param position (Optional) position in underlying book list. Only used in
     *                 read-only view.
     */
    public static void openBook(Activity a, long id, BooklistBuilder builder, Integer position) {
        if (BookCataloguePreferences.getOpenBookReadOnly()) {
            // Make a flattened copy of the list of books, if available
            String listTable = null;
            if (builder != null) {
                listTable = builder.createFlattenedBooklist().getTable().getName();
            }
            viewBook(a, id, listTable, position);
        } else {
            editBook(a, id, BookEdit.TAB_EDIT);
        }
    }

    /**
     * Load the EditBook activity based on the provided id in edit mode. Also
     * open to the provided tab.
     *
     * @param id  The id of the book to edit
     * @param tab Which tab to open first
     */
    public static void editBook(Activity a, long id, int tab) {
        Intent i = new Intent(a, BookEdit.class);
        i.putExtra(ColumnInfo.KEY_ROWID, id);
        i.putExtra(BookEdit.TAB, tab);
        a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
        return;
    }

    /**
     * Load the EditBook tab activity in read-only mode. The first tab is book
     * details.
     *
     * @param a         current activity from which we start
     * @param id        The id of the book to view
     * @param listTable (Optional) name of the temp table containing a list of book
     *                  IDs.
     * @param position  (Optional) position in underlying book list. Only used in
     *                  read-only view.
     */
    public static void viewBook(Activity a, long id, String listTable, Integer position) {
        Intent i = new Intent(a, BookEdit.class);
        i.putExtra(BKEY_FLATTENED_BOOKLIST, listTable);
        if (position != null) {
            i.putExtra(FLATTENED_BOOKLIST_POSITION, position);
        }
        i.putExtra(ColumnInfo.KEY_ROWID, id);
        i.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT); // needed extra for creating BookEdit
        i.putExtra(BookEdit.KEY_READ_ONLY, true);
        a.startActivityForResult(i, UniqueId.ACTIVITY_VIEW_BOOK);
        return;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_book_base;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);
        mDb.open();

        // Get the extras; we use them a lot
        Bundle extras = getIntent().getExtras();

        // We need the row ID
        Long rowId = savedInstanceState != null ? savedInstanceState.getLong(ColumnInfo.KEY_ROWID) : null;
        if (rowId == null) {
            rowId = extras != null ? extras.getLong(ColumnInfo.KEY_ROWID) : null;
        }
        mRowId = (rowId == null) ? 0 : rowId;
        boolean isExistingBook = (mRowId > 0);

        // Get the book data from the bundle or the database
        loadBookData(mRowId, savedInstanceState == null ? extras : savedInstanceState);

        mIsReadOnly = (extras != null)
                && extras.getBoolean(KEY_READ_ONLY, false)
                && isExistingBook;

        mTabLayout = findViewById(R.id.tabpanel);
        mTabLayout.addOnTabSelectedListener(new TabListener());

        if (mIsReadOnly) {
            BookDetailsReadOnly details = new BookDetailsReadOnly();
            details.setArguments(extras);
            replaceTab(details);

            mTabLayout.setVisibility(View.GONE);
            findViewById(R.id.buttonbar_cancel_save).setVisibility(View.GONE);
        } else {
            ArrayList<TabLayout.Tab> mAllTabs = new ArrayList<>();
            try {

                TabLayout.Tab tab;
                tab = mTabLayout.newTab().setText(R.string.details).setTag(mTabClasses[TAB_EDIT].newInstance());
                mTabLayout.addTab(tab);
                mAllTabs.add(tab);

                tab = mTabLayout.newTab().setText(R.string.notes).setTag(mTabClasses[TAB_EDIT_NOTES].newInstance());
                mTabLayout.addTab(tab);
                mAllTabs.add(tab);

                if (isExistingBook) {
                    tab = mTabLayout.newTab().setText(R.string.loan).setTag(mTabClasses[TAB_EDIT_FRIENDS].newInstance());
                    mTabLayout.addTab(tab);
                    mAllTabs.add(tab);

                    boolean isAnthology = (mBookData.getRowId() > 0) && (mBookData.getInt(BookData.KEY_ANTHOLOGY) != 0);
                    setShowAnthology(isAnthology);
                }
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Creating BookEdit tabs failed?");
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
                // Cleanup because we may have made global changes
                mDb.purgeAuthors();
                mDb.purgeSeries();
                // We're done.
                setResult(Activity.RESULT_OK);

                if (isDirty()) {
                    StandardDialogs.showConfirmUnsavedEditsDialog(BookEdit.this, null);
                } else {
                    finish();
                }
            }
        });

        initBooklist(extras, savedInstanceState);

        // Must come after all book data and list retrieved.
        setActivityTitle();

        Tracker.exitOnCreate(this);
    }

    private void replaceTab(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    /**
     * If we are passed a flat book list, get it and validate it
     */
    private void initBooklist(Bundle extras, Bundle savedInstanceState) {
        if (extras != null) {
            String list = extras.getString(BKEY_FLATTENED_BOOKLIST);
            if (list != null && !list.isEmpty()) {
                mList = new FlattenedBooklist(mDb.getDb(), list);
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
                    while (!mList.getBookId().equals(mRowId)) {
                        if (!mList.moveNext())
                            break;
                    }
                    if (!mList.getBookId().equals(mRowId)) {
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
     * We override the dispatcher because the ScrollView will consume
     * all events otherwise.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
            return true;
        super.dispatchTouchEvent(event);
        // Always return true; we want the events.
        return true;
    }

    /**
     * This function will populate the forms elements in three different ways 1.
     * If a valid rowId exists it will populate the fields from the database 2.
     * If fields have been passed from another activity (e.g. ISBNSearch) it
     * will populate the fields from the bundle 3. It will leave the fields
     * blank for new books.
     */
    private void loadBookData(Long rowId, Bundle bestBundle) {
        if (bestBundle != null && bestBundle.containsKey(BOOK_DATA)) {
            // If we have saved book data, use it
            mBookData = new BookData(rowId, bestBundle.getBundle(BOOK_DATA));
        } else {
            // Just load based on rowId
            mBookData = new BookData(rowId);
        }
    }

    /**
     * This is a straight passthrough
     */
    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // 1. the call to duplicateBook() no longer uses this ID
        // 2. We can't just finish(); there might be unsaved edits.
        // 3. if we want to finish on creating a new book, we should do it when
        // we start the activity
        // switch (requestCode) {
        // setResult(resultCode, intent);
        // finish();
        // break;
        // }
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
     * <p>
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
    protected void onSaveInstanceState(Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);
        super.onSaveInstanceState(outState);

        outState.putLong(ColumnInfo.KEY_ROWID, mRowId);
        outState.putBundle(BOOK_DATA, mBookData.getRawData());
        if (mList != null) {
            outState.putInt(FLATTENED_BOOKLIST_POSITION, (int) mList.getPosition());
        }
        outState.putInt(BookEdit.TAB, mTabLayout.getSelectedTabPosition());
        Tracker.exitOnSaveInstanceState(this);
    }

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
    public void setDirty(boolean dirty) {
        mIsDirtyFlg = dirty;
    }

    /**
     * If 'back' is pressed, and the user has made changes, ask them if they
     * really want to lose the changes.
     * <p>
     * We don't use onBackPressed because it does not work with API level 4.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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
        Intent i = new Intent();
        i.putExtra(ColumnInfo.KEY_ROWID, mBookData.getRowId());
        setResult(Activity.RESULT_OK, i);
        finish();
    }

    /**
     * Show or hide the anthology tab
     * FIXME:  android:ellipsize="end" and maxLine="1" on the TextView used by the mAnthologyTab... how ?
     */
    public void setShowAnthology(boolean showAnthology) {
        if (showAnthology) {
            if (mAnthologyTab == null) {
                try {
                    mAnthologyTab = mTabLayout.newTab()
                            .setText(R.string.anthology_titles)
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

    @Override
    public BookData getBookData() {
        return mBookData;
    }

    @Override
    public void setRowId(Long id) {
        if (mRowId != id) {
            mRowId = id;
            loadBookData(id, null);
            Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
            if (frag instanceof DataEditor) {
                ((DataEditor) frag).reloadData(mBookData);
            }
            setActivityTitle();
        }
    }

    /**
     * Validate the current data in all fields that have validators. Display any errors.
     *
     * @return Boolean success or failure.
     */
    private void validate() {
        if (!mBookData.validate()) {
            Toast.makeText(this, mBookData.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * This will save a book into the database, by either updating or created a
     * book. Minor modifications will be made to the strings: - Titles will be
     * rewords so 'a', 'the', 'an' will be moved to the end of the string (this
     * is only done for NEW books)
     * <p>
     * - Date published will be converted from a date to a string
     * <p>
     * Thumbnails will also be saved to the correct location
     * <p>
     * It will check if the book already exists (isbn search) if you are
     * creating a book; if so the user will be prompted to confirm.
     * <p>
     * In all cases, once the book is added/created, or not, the appropriate
     * method of the passed nextStep parameter will be executed. Passing
     * nextStep is necessary because this method may return after displaying a
     * dialogue.
     *
     * @param nextStep The next step to be executed on success/failure.
     */
    private void saveState(final PostSaveAction nextStep) {
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
        if (!mBookData.containsKey(ColumnInfo.KEY_TITLE)
                || mBookData.getString(ColumnInfo.KEY_TITLE).trim().isEmpty()) {
            Toast.makeText(this, getResources().getText(R.string.title_required), Toast.LENGTH_LONG).show();
            return;
        }

        if (mRowId == 0) {
            String isbn = mBookData.getString(ColumnInfo.KEY_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty()) {
                if (mDb.checkIsbnExists(isbn, true)) {
                    /*
                     * If it exists, show a dialog and use it to perform the
                     * next action, according to the users choice.
                     */
                    SaveAlert alert = new SaveAlert();
                    alert.setMessage(getResources().getString(R.string.duplicate_book_message));
                    alert.setTitle(R.string.duplicate_book_title);
                    alert.setIcon(android.R.drawable.ic_menu_info_details);
                    alert.setButton(SaveAlert.BUTTON_POSITIVE,
                            this.getResources().getString(android.R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    updateOrCreate();
                                    nextStep.success();
                                    return;
                                }
                            });
                    alert.setButton(SaveAlert.BUTTON_NEGATIVE,
                            this.getResources().getString(android.R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    nextStep.failure();
                                    return;
                                }
                            });
                    alert.show();
                    return;
                }
            }
        }

        // No special actions required...just do it.
        updateOrCreate();
        nextStep.success();
        return;
    }

    /**
     * Save the collected book details
     */
    private void updateOrCreate() {
        if (mRowId == 0) {
            long id = mDb.createBook(mBookData, 0);

            if (id > 0) {
                setRowId(id);
                File thumb = ImageUtils.getTempThumbnail();
                File real = ImageUtils.fetchThumbnailByUuid(mDb.getBookUuid(mRowId));
                //noinspection ResultOfMethodCallIgnored
                thumb.renameTo(real);
            }
        } else {
            mDb.updateBook(mRowId, mBookData, 0);
        }

        /*
         * These are global variables that will be sent via intent back to the
         * list view, if added/created
         */
        try {
            ArrayList<Author> authors = mBookData.getAuthors();
            if (authors.size() > 0) {
                added_author = authors.get(0).getSortName();
            } else {
                added_author = "";
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        try {
            ArrayList<Series> series = mBookData.getSeries();
            if (series.size() > 0)
                added_series = series.get(0).name;
            else
                added_series = "";
        } catch (Exception e) {
            Logger.logError(e);
        }

        added_title = mBookData.getString(ColumnInfo.KEY_TITLE);
        added_genre = mBookData.getString(ColumnInfo.KEY_GENRE);
    }

    /**
     * Sets title of the parent activity depending on show/edit/add
     */
    private void setActivityTitle() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            if (mIsReadOnly && mList != null) {
                // display a book
//                bar.setTitle(mBookData.getString(KEY_TITLE));
//                bar.setSubtitle(mBookData.getAuthorTextShort()
//                                + String.format(" (" + getResources().getString(R.string.x_of_y) + ")",
//                                  mList.getAbsolutePosition(), mList.getCount())
//                );
                bar.setTitle(this.getResources().getString(R.string.book_details));
                bar.setSubtitle(null);

            } else if (mBookData.getRowId() > 0) {
                // editing an existing book
                bar.setTitle(mBookData.getString(ColumnInfo.KEY_TITLE));
                bar.setSubtitle(mBookData.getAuthorTextShort());
            } else {
                // new book
                bar.setTitle(this.getResources().getString(R.string.menu_insert));
                bar.setSubtitle(null);
            }
        }
    }

    /**
     * Load a publisher list; reloading this list every time a tab changes is
     * slow. So we cache it.
     *
     * @return List of publishers
     */
    @Override
    public ArrayList<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = new ArrayList<>();

            try (Cursor publisher_cur = mDb.fetchAllPublishers()) {
                final int col = publisher_cur.getColumnIndexOrThrow(ColumnInfo.KEY_PUBLISHER);
                while (publisher_cur.moveToNext()) {
                    mPublishers.add(publisher_cur.getString(col));
                }
            }
        }
        return mPublishers;
    }

    /**
     * Load a genre list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    @Override
    public ArrayList<String> getGenres() {
        if (mGenres == null) {
            mGenres = new ArrayList<>();

            try (Cursor genre_cur = mDb.fetchAllGenres("")) {
                final int col = genre_cur.getColumnIndexOrThrow(ColumnInfo.KEY_ROWID);
                while (genre_cur.moveToNext()) {
                    mGenres.add(genre_cur.getString(col));
                }
            }
        }
        return mGenres;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages
     */
    @Override
    public ArrayList<String> getLanguages() {
        if (mLanguages == null) {
            mLanguages = new ArrayList<>();

            try (Cursor cur = mDb.fetchAllLanguages("")) {
                final int col = cur.getColumnIndexOrThrow(ColumnInfo.KEY_ROWID);
                while (cur.moveToNext()) {
                    String s = cur.getString(col);
                    if (s != null && !s.isEmpty()) {
                        mLanguages.add(cur.getString(col));
                    }
                }
            }
        }
        return mLanguages;
    }

    /**
     * Load a format list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    public ArrayList<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnPartialDatePickerListener) {
            ((OnPartialDatePickerListener) frag).onPartialDatePickerSet(dialogId, dialog, year, month, day);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received date dialog result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible())
            dialog.dismiss();
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnPartialDatePickerListener) {
            ((OnPartialDatePickerListener) frag).onPartialDatePickerCancel(dialogId, dialog);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received date dialog cancellation with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible())
            dialog.dismiss();

    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnTextFieldEditorListener) {
            ((OnTextFieldEditorListener) frag).onTextFieldEditorSave(dialogId, dialog, newText);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onTextFieldEditorSave result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible())
            dialog.dismiss();
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnTextFieldEditorListener) {
            ((OnTextFieldEditorListener) frag).onTextFieldEditorCancel(dialogId, dialog);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onTextFieldEditorCancel result with no fragment to handle it"));
        }
        // Make sure it's dismissed
        if (dialog.isVisible())
            dialog.dismiss();
    }

    /**
     * Dialog handler; pass results to relevant destination
     */
    @Override
    public void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog, boolean checked, String shelf,
                                        String textList, String encodedList) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof OnBookshelfCheckChangeListener) {
            ((OnBookshelfCheckChangeListener) frag).onBookshelfCheckChanged(dialogId, dialog, checked, shelf, textList, encodedList);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onBookshelfCheckChanged result with no fragment to handle it"));
        }
    }

    /**
     * menu handler; handle the 'home' key, otherwise, pass on the event
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                doFinish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    ////////////////////////////////////
    // Standard STATIC Methods
    // //////////////////////////////////

    public interface PostSaveAction {
        void success();

        @SuppressWarnings("EmptyMethod")
        void failure();
    }

    private class TabListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            replaceTab((Fragment) tab.getTag());
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
        }
    }

    private class SaveAlert extends AlertDialog {

        SaveAlert() {
            super(BookEdit.this);
        }
    }

    private class DoConfirmAction implements PostSaveAction {

        DoConfirmAction() {
        }

        public void success() {
            Intent i = new Intent();
            i.putExtra(ColumnInfo.KEY_ROWID, mBookData.getRowId());
            i.putExtra(ADDED_HAS_INFO, true);
            i.putExtra(ADDED_GENRE, added_genre);
            i.putExtra(ADDED_SERIES, added_series);
            i.putExtra(ADDED_TITLE, added_title);
            i.putExtra(ADDED_AUTHOR, added_author);

            setResult(Activity.RESULT_OK, i);
            finish();
        }

        public void failure() {
            // Do nothing
        }
    }
}

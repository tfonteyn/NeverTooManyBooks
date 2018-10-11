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
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
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
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A tab host activity which holds the edit book tabs
 * 1. Details
 * 2. Notes
 * 3. Loan Book
 * 4. Anthology titles
 *
 * @author Evan Leybourn
 */
public class EditBookActivity extends BookCatalogueActivity
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
    private boolean mIsDirtyFlg = false;
    private long mRowId;
    @Nullable
    private Book mBook;

    private TabLayout mTabLayout;
    @Nullable
    private TabLayout.Tab mAnthologyTab;

    /** Lists in database so far, we cache them for performance */
    private List<String> mFormats;
    private List<String> mGenres;
    private List<String> mLanguages;
    private List<String> mLocations;
    private List<String> mPublishers;

    /**
     * Load with the provided book id. Also open to the provided tab.
     *
     * @param id  The id of the book to edit
     * @param tab Which tab to open first
     */
    public static void startActivity(@NonNull final Activity activity,
                                     final long id,
                                     final int tab) {
        Intent intent = new Intent(activity, EditBookActivity.class);
        intent.putExtra(UniqueId.KEY_ID, id);
        intent.putExtra(EditBookActivity.TAB, tab);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK);
    }

    /**
     * Load with a new book
     */
    public static void startActivity(@NonNull final Activity activity,
                                     @NonNull final Bundle bookData) {
        Intent intent = new Intent(activity, EditBookActivity.class);
        intent.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK);
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

        Bundle extras = getIntent().getExtras();

        mRowId = getBookId(savedInstanceState, extras);
        boolean isExistingBook = (mRowId > 0);

        mBook = initBook(mRowId, savedInstanceState == null ? extras : savedInstanceState);

        mTabLayout = findViewById(R.id.tab_panel);

        initTabListener();
        initForEditing(extras, isExistingBook);
        initCancelConfirmButtons(isExistingBook);

        // Must come after all book data and list retrieved.
        initActivityTitle();

        Tracker.exitOnCreate(this);
    }

    /**
     * get the book id either from the savedInstanceState or the extras.
     */
    private long getBookId(final @Nullable Bundle savedInstanceState, final @Nullable Bundle extras) {
        long bookId = 0;
        if (savedInstanceState != null) {
            bookId = savedInstanceState.getLong(UniqueId.KEY_ID);
        }
        if ((bookId == 0) && (extras != null)) {
            bookId = extras.getLong(UniqueId.KEY_ID);
        }
        return bookId;
    }

    /**
     * initial setup for editing
     */
    private void initForEditing(@Nullable final Bundle extras, final boolean isExistingBook) {

        ArrayList<TabLayout.Tab> mAllTabs = new ArrayList<>();
        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        try {
            Holder holder = new Holder();
            holder.fragment = (Fragment) mTabClasses[TAB_EDIT].newInstance();
            TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.details).setTag(holder);
            mTabLayout.addTab(tab);
            mAllTabs.add(tab);

            holder = new Holder();
            holder.fragment = (Fragment) mTabClasses[TAB_EDIT_NOTES].newInstance();
            tab = mTabLayout.newTab().setText(R.string.notes).setTag(holder);
            mTabLayout.addTab(tab);
            mAllTabs.add(tab);

            Integer isAnthology = mBook.getInt(Book.IS_ANTHOLOGY);
            addAnthologyTab(isAnthology > 0);

            // can't loan out a new book yet
            if (isExistingBook) {
                holder = new Holder();
                holder.fragment = (Fragment) mTabClasses[TAB_EDIT_FRIENDS].newInstance();
                tab = mTabLayout.newTab().setText(R.string.loan).setTag(holder);
                mTabLayout.addTab(tab);
                mAllTabs.add(tab);
            }
        } catch (@NonNull InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Creating BookDetailsActivity tabs failed?");
        }

        // any specific tab desired as 'selected' ?
        if (extras != null && extras.containsKey(TAB)) {
            int i = extras.getInt(TAB);
            if (mAllTabs.size() > i) {
                mAllTabs.get(i).select();
            }
        } else {
            mAllTabs.get(TAB_EDIT).select();
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

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Cleanup because we may have made global changes TODO: detect this if actually needed
                mDb.purgeAuthors();
                mDb.purgeSeries();
                // We're done.
                setResult(Activity.RESULT_OK);

                if (isDirty()) {
                    StandardDialogs.showConfirmUnsavedEditsDialog(EditBookActivity.this, null);
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
    @NonNull
    private Book initBook(final long bookId, @Nullable final Bundle bestBundle) {
        if (bestBundle != null && bestBundle.containsKey(UniqueId.BKEY_BOOK_DATA)) {
            // If we have saved book data, use it
            return new Book(bookId, bestBundle.getBundle(UniqueId.BKEY_BOOK_DATA));
        } else {
            // Just load based on rowId
            return new Book(bookId);
        }
    }

    /**
     * Sets title of the activity depending on show/edit/new
     */
    private void initActivityTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (mRowId > 0) {
                // editing an existing book
                actionBar.setTitle(mBook.getString(UniqueId.KEY_TITLE));
                actionBar.setSubtitle(mBook.getAuthorTextShort());
            } else {
                // new book
                actionBar.setTitle(R.string.menu_insert);
                actionBar.setSubtitle(null);
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

    @Override
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDb.close();
        Tracker.exitOnDestroy(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);
        super.onSaveInstanceState(outState);

        outState.putLong(UniqueId.KEY_ID, mRowId);
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, mBook.getRawData());
        outState.putInt(EditBookActivity.TAB, mTabLayout.getSelectedTabPosition());
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
        intent.putExtra(UniqueId.KEY_ID, mBook.getBookId());
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
            StandardDialogs.showQuickNotice(this, R.string.unexpected_error);
            Logger.error("Received date dialog result with no fragment to handle it");
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
            StandardDialogs.showQuickNotice(this, R.string.unexpected_error);
            Logger.error("Received date dialog cancellation with no fragment to handle it");
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
            StandardDialogs.showQuickNotice(this, R.string.unexpected_error);
            Logger.error("Received onTextFieldEditorSave result with no fragment to handle it");
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
            StandardDialogs.showQuickNotice(this, R.string.unexpected_error);
            Logger.error("Received onTextFieldEditorCancel result with no fragment to handle it");
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
            StandardDialogs.showQuickNotice(this, R.string.unexpected_error);
            Logger.error("Received onBookshelfCheckChanged result with no fragment to handle it");
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

    @Nullable
    @Override
    public Book getBook() {
        return mBook;
    }

    @Override
    public void setRowId(final long id) {
        if (mRowId != id) {
            mRowId = id;
            mBook = initBook(id, null);
            Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
            if (frag instanceof DataEditor) {
                ((DataEditor) frag).reloadData(mBook);
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

    private void initTabListener() {
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                Holder holder = (Holder) tab.getTag();
                replaceFragment(holder.fragment);
            }

            @Override
            public void onTabUnselected(@NonNull final TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(@NonNull final TabLayout.Tab tab) {
            }
        });
    }

    private void replaceFragment(@NonNull final Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    /**
     * add or remove the anthology tab
     */
    public void addAnthologyTab(final boolean show) {
        if (show) {
            if (mAnthologyTab == null) {
                Holder holder = new Holder();
                try {
                    holder.fragment = (Fragment) mTabClasses[TAB_EDIT_ANTHOLOGY].newInstance();
                    mAnthologyTab = mTabLayout.newTab()
                            .setText(R.string.anthology)
                            .setTag(holder);
                } catch (@NonNull InstantiationException | IllegalAccessException ignore) {
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
        if (!mBook.validate()) {
            StandardDialogs.showQuickNotice(this, mBook.getValidationExceptionMessage(getResources()));
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
            ((DataEditor) frag).saveAllEdits(mBook);
        }

        // Ignore validation failures; we still validate to get the current values.
        validate();

        // However, there is some data that we really do require...
        if (mBook.getAuthorList().size() == 0) {
            StandardDialogs.showQuickNotice(this, R.string.author_required);
            return;
        }
        if (!mBook.containsKey(UniqueId.KEY_TITLE)
                || mBook.getString(UniqueId.KEY_TITLE).isEmpty()) {
            StandardDialogs.showQuickNotice(this, R.string.title_required);
            return;
        }

        if (mRowId == 0) {
            String isbn = mBook.getString(UniqueId.KEY_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && ((mDb.getIdFromIsbn(isbn, true) > 0))) {
                /*
                 * If it exists, show a dialog and use it to perform the
                 * next action, according to the users choice.
                 */
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.duplicate_book_message))
                        .setTitle(R.string.duplicate_book_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .create();

                dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                updateOrInsert();
                                nextStep.success();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        getString(android.R.string.cancel),
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
            long id = mDb.insertBook(mBook);

            if (id > 0) {
                setRowId(id);
                File thumb = StorageUtils.getTempCoverFile();
                File real = StorageUtils.getCoverFile(mDb.getBookUuid(mRowId));
                StorageUtils.renameFile(thumb, real);
            }
        } else {
            mDb.updateBook(mRowId, mBook, 0);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public interface PostSaveAction {
        void success();

        @SuppressWarnings("EmptyMethod")
        void failure();
    }

    private class Holder {
        Fragment fragment;
    }

    private class DoConfirmAction implements PostSaveAction {

        DoConfirmAction() {
        }

        public void success() {
            Intent intent = new Intent();
            intent.putExtra(UniqueId.KEY_ID, mBook.getBookId());
            setResult(Activity.RESULT_OK, intent);
            finish();
        }

        public void failure() {
            // Do nothing
        }
    }
}

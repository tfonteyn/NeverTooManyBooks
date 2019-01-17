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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.google.android.material.tabs.TabLayout;

import java.io.File;

/**
 * A tab host activity which holds the edit book tabs.
 * 1. Details
 * 2. Notes
 * 3. Anthology titles
 * 4. Loan Book -> ENHANCE: remove this from this activity into a DialogFragment
 */
public class EditBookFragment
        extends BookBaseFragment
        implements BookManager {

    public static final String TAG = "EditBookFragment";

    /**
     * Tabs in order.
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_LOANS = 2;
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    /** the one and only book we're editing. */
    private Book mBook;

    /** cache our activity to avoid multiple requireActivity and casting. */
    private BaseActivity mActivity;

    /** The tabs. */
    private TabLayout mTabLayout;
    /** The TOC tab; is hidden/visible depending on the book. */
    @Nullable
    private TabLayout.Tab mAnthologyTab;

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="BookManager interface">

    @Override
    @NonNull
    public BookManager getBookManager() {
        return this;
    }

    @Override
    @NonNull
    public Book getBook() {
        return mBook;
    }

    @Override
    public void setBook(@NonNull final Book book) {
        mBook = book;
    }

    public boolean isDirty() {
        return mActivity.isDirty();
    }

    public void setDirty(final boolean isDirty) {
        mActivity.setDirty(isDirty);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        // cache to avoid multiple calls to requireActivity()
        mActivity = (BaseActivity) requireActivity();

        super.onActivityCreated(savedInstanceState);

        boolean isExistingBook = (getBook().getBookId() > 0);
        initTabs(isExistingBook, savedInstanceState);

        //noinspection ConstantConditions
        Button confirmButton = getView().findViewById(R.id.confirm);
        confirmButton.setText(isExistingBook ? R.string.btn_confirm_save_book
                                             : R.string.btn_confirm_add_book);

        confirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                doSave(new StandardDialogs.AlertDialogAction() {
                    @Override
                    public void onPositive() {
                        saveBook();
                        Intent data = new Intent();
                        data.putExtra(UniqueId.KEY_ID, getBook().getBookId());
                        mActivity.setResult(Activity.RESULT_OK, data);
                        mActivity.finish();
                    }

                    @Override
                    public void onNeutral() {
                        // Remain editing
                    }

                    @Override
                    public void onNegative() {
                        doCancel();
                    }
                });
            }
        });

        getView().findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                doCancel();
            }
        });

        Tracker.exitOnActivityCreated(this);
    }

//    @Override
//    protected void initFields() {
//        super.initFields();
//    }

//    @Override
//    protected void onLoadFieldsFromBook(@NonNull final Book book, final boolean setAllFrom) {
//        super.onLoadFieldsFromBook(book, setAllFrom);
//    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    /**
     * initial setup for editing.
     */
    private void initTabs(final boolean isExistingBook,
                          @Nullable final Bundle savedInstanceState) {

        //noinspection ConstantConditions
        mTabLayout = getView().findViewById(R.id.tab_panel);

        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        FragmentHolder fragmentHolder;

        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new EditBookFieldsFragment();
        fragmentHolder.tag = EditBookFieldsFragment.TAG;
        TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.tab_lbl_details).setTag(
                fragmentHolder);
        mTabLayout.addTab(tab);

        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new EditBookNotesFragment();
        fragmentHolder.tag = EditBookNotesFragment.TAG;
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_notes).setTag(fragmentHolder);
        mTabLayout.addTab(tab);

        addTOCTab(getBook().getBoolean(Book.HAS_MULTIPLE_WORKS));

        // can't loan out a new book yet (or user does not like loaning)
        if (isExistingBook && Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO)) {
            fragmentHolder = new FragmentHolder();
            fragmentHolder.fragment = new EditBookLoanedFragment();
            fragmentHolder.tag = EditBookLoanedFragment.TAG;
            tab = mTabLayout.newTab().setText(R.string.tab_lbl_loan).setTag(fragmentHolder);
            mTabLayout.addTab(tab);
        }


        Bundle args = getArguments();
        // any specific tab desired as 'selected' ?
        int tabWanted = BundleUtils.getIntFromBundles(REQUEST_BKEY_TAB, savedInstanceState, args);

        int showTab = TAB_EDIT;
        switch (tabWanted) {
            case TAB_EDIT:
            case TAB_EDIT_NOTES:
                showTab = tabWanted;
                break;

            case TAB_EDIT_LOANS:
                if (Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO)) {
                    showTab = tabWanted;
                }
                break;

            case TAB_EDIT_ANTHOLOGY:
                if (Fields.isVisible(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK)) {
                    showTab = tabWanted;
                }
                break;

            default:
                Logger.error("Unknown tab=" + tabWanted);
                break;
        }

        TabLayout.Tab ourTab = mTabLayout.getTabAt(showTab);
        //noinspection ConstantConditions
        ourTab.select();

        fragmentHolder = (FragmentHolder) ourTab.getTag();
        //noinspection ConstantConditions
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.tab_fragment, fragmentHolder.fragment, fragmentHolder.tag)
                .commit();

        // finally hook up our listener.
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                FragmentHolder fragmentHolder = (FragmentHolder) tab.getTag();
                //noinspection ConstantConditions
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.tab_fragment, fragmentHolder.fragment, fragmentHolder.tag)
                        .commit();
            }

            @Override
            public void onTabUnselected(@NonNull final TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(@NonNull final TabLayout.Tab tab) {
            }
        });
    }

    /**
     * add or remove the anthology tab.
     */
    void addTOCTab(final boolean show) {
        if (show) {
            if (mAnthologyTab == null) {
                FragmentHolder fragmentHolder = new FragmentHolder();
                fragmentHolder.fragment = new EditBookTOCFragment();
                fragmentHolder.tag = EditBookTOCFragment.TAG;
                mAnthologyTab = mTabLayout.newTab()
                                          .setText(R.string.tab_lbl_content)
                                          .setTag(fragmentHolder);

            }
            mTabLayout.addTab(mAnthologyTab);
        } else {
            if (mAnthologyTab != null) {
                mTabLayout.removeTab(mAnthologyTab);
            }
            mAnthologyTab = null;
        }
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

//    @Override
//    @CallSuper
//    protected void onSaveFieldsToBook(@NonNull final Book book) {
//        super.onSaveFieldsToBook(book);
//    }

    /**
     * the only thing on this level is the TAB we're on.
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putInt(REQUEST_BKEY_TAB, mTabLayout.getSelectedTabPosition());
        super.onSaveInstanceState(outState);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // do nothing here. Child fragments will add their own menus
    }

    /**
     * Called when the user clicks 'save'.
     * <p>
     * This will save a book into the database, by either updating or created a book.
     * <p>
     * It will check if the book already exists (isbn search) if you are creating a book;
     * if so the user will be prompted to confirm.
     * <p>
     * In all cases, once the book is added/created, or not, the appropriate method of the
     * passed nextStep parameter will be executed. Passing nextStep is necessary because
     * this method may return after displaying a dialogue.
     *
     * @param nextStep The next step to be executed on confirm/cancel.
     */
    private void doSave(@NonNull final StandardDialogs.AlertDialogAction nextStep) {
        Book book = getBook();

        // ask the currently displayed tab fragment to add it's fields; the others
        // did when they went in hiding
        //ENHANCE: alternative method: see SearchAdminActivity. Decide later
        //noinspection ConstantConditions
        DataEditor currentChildFragment = (DataEditor) getChildFragmentManager()
                .findFragmentById(R.id.tab_fragment);
        //noinspection ConstantConditions
        currentChildFragment.saveFieldsTo(book);


        // Ignore validation failures; but we still validate to get the current values.
        book.validate();
//        if (!book.validate()) {
//              StandardDialogs.sendTaskUserMessage(this,
//               book.getValidationExceptionMessage(getResources()));
//        }

        // However, there is some data that we really do require...
        if (book.getAuthorList().size() == 0) {
            StandardDialogs.showUserMessage(mActivity, R.string.warning_required_author_long);
            return;
        }
        if (!book.containsKey(UniqueId.KEY_TITLE) || book.getString(UniqueId.KEY_TITLE).isEmpty()) {
            StandardDialogs.showUserMessage(mActivity, R.string.warning_required_title);
            return;
        }

        if (book.getBookId() == 0) {
            String isbn = book.getString(UniqueId.KEY_BOOK_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && ((mDb.getIdFromIsbn(isbn, true) > 0))) {
                StandardDialogs.confirmSaveDuplicateBook(requireContext(), nextStep);
                return;
            }
        }

        // No special actions required...just do it.
        nextStep.onPositive();
    }

    private void doCancel() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, getBook().getBookId());
        //ENHANCE: global changes not detected, so assume they happened.
        mActivity.setResult(Activity.RESULT_OK, data);
        mActivity.finishIfClean();
    }

    /**
     * Save the collected book details.
     */
    private void saveBook() {
        Book book = getBook();
        if (book.getBookId() == 0) {
            long id = mDb.insertBook(book);
            if (id > 0) {
                // if we got a cover while searching the internet, make it permanent
                if (book.getBoolean(UniqueId.BKEY_HAVE_THUMBNAIL)) {
                    String uuid = mDb.getBookUuid(id);
                    // get the temporary downloaded file
                    File source = StorageUtils.getTempCoverFile();
                    File destination = StorageUtils.getCoverFile(uuid);
                    // and rename it to the permanent UUID one.
                    StorageUtils.renameFile(source, destination);
                }
            }
        } else {
            mDb.updateBook(book.getBookId(), book, 0);
        }
    }

    private static class FragmentHolder {

        Fragment fragment;
        String tag;
    }

}

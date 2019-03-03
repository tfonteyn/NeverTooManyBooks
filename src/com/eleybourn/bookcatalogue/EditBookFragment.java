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
import androidx.fragment.app.FragmentTransaction;

import java.io.File;

import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.dialogs.AlertDialogListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.google.android.material.tabs.TabLayout;

/**
 * A tab host activity which holds the edit book tabs.
 * 1. Details
 * 2. Notes
 * 3. Anthology titles
 */
public class EditBookFragment
        extends EditBookBaseFragment
        implements BookManager, DataEditor {

    /** Fragment manager tag. */
    public static final String TAG = EditBookFragment.class.getSimpleName();

    /**
     * Tabs in order.
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    public static final int TAB_EDIT = 0;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_NOTES = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_ANTHOLOGY = 2;

    /** the one and only book we're editing. */
    private Book mBook;

    /** cache our activity to avoid multiple requireActivity and casting. */
    private EditBookActivity mActivity;

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

    /**
     * Delegate to the Activity which handles the 'back' button.
     *
     * @return <tt>true</tt> if our data was changed.
     */
    public boolean isDirty() {
        return mActivity.isDirty();
    }

    /**
     * Delegate to the Activity which handles the 'back' button.
     *
     * @param isDirty set to <tt>true</tt> if our data was changed.
     */
    public void setDirty(final boolean isDirty) {
        mActivity.setDirty(isDirty);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mActivity = (EditBookActivity) requireActivity();
        super.onActivityCreated(savedInstanceState);

        boolean isExistingBook = getBook().getId() > 0;
        initTabs(savedInstanceState);

        Button confirmButton = requireView().findViewById(R.id.confirm);
        confirmButton.setText(isExistingBook ? R.string.btn_confirm_save_book
                                             : R.string.btn_confirm_add_book);

        confirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                doSave(new AlertDialogListener() {
                    @Override
                    public void onPositiveButton() {
                        saveBook();
                        Intent data = new Intent();
                        data.putExtra(UniqueId.KEY_ID, getBook().getId());
                        mActivity.setResult(Activity.RESULT_OK, data);
                        mActivity.finish();
                    }

                    @Override
                    public void onNeutralButton() {
                        // Remain editing
                    }

                    @Override
                    public void onNegativeButton() {
                        doCancel();
                    }
                });
            }
        });

        requireView().findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                doCancel();
            }
        });
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    /**
     * initial setup for editing.
     */
    private void initTabs(@Nullable final Bundle savedInstanceState) {

        mTabLayout = requireView().findViewById(R.id.tab_panel);
        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);

        FragmentHolder holder;
        TabLayout.Tab tab;

        holder = new FragmentHolder(EditBookFieldsFragment.TAG);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_details).setTag(holder);
        mTabLayout.addTab(tab);

        holder = new FragmentHolder(EditBookNotesFragment.TAG);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_notes).setTag(holder);
        mTabLayout.addTab(tab);

        addTOCTab(getBook().isBitSet(UniqueId.KEY_BOOK_TOC_BITMASK,
                                     TocEntry.Type.MULTIPLE_WORKS));

        // any specific tab desired as 'selected' ?
        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        int tabWanted = args.getInt(REQUEST_BKEY_TAB, TAB_EDIT);

        int showTab = TAB_EDIT;
        switch (tabWanted) {
            case TAB_EDIT:
            case TAB_EDIT_NOTES:
                showTab = tabWanted;
                break;

            case TAB_EDIT_ANTHOLOGY:
                if (Fields.isVisible(UniqueId.KEY_BOOK_TOC_BITMASK)) {
                    showTab = tabWanted;
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown tab=" + tabWanted);
        }

        TabLayout.Tab ourTab = mTabLayout.getTabAt(showTab);
        //noinspection ConstantConditions
        ourTab.select();

        holder = (FragmentHolder) ourTab.getTag();
        //noinspection ConstantConditions
        getChildFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                // replace as this is a tab bar
                .replace(R.id.tab_fragment, holder.fragment, holder.tag)
                .commit();

        // finally hook up our listener.
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                FragmentHolder fragmentHolder = (FragmentHolder) tab.getTag();
                //noinspection ConstantConditions
                getChildFragmentManager()
                        .beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        // replace as this is a tab bar
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
     *
     * @param show <tt>true</tt> to enable the TOC tab.
     */
    void addTOCTab(final boolean show) {
        if (show) {
            if (mAnthologyTab == null) {
                FragmentHolder fragmentHolder = new FragmentHolder(EditBookTOCFragment.TAG);
                mAnthologyTab = mTabLayout.newTab().setText(R.string.tab_lbl_content)
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
        super.onSaveInstanceState(outState);
        outState.putInt(REQUEST_BKEY_TAB, mTabLayout.getSelectedTabPosition());
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
     * Save a book into the database, by either updating or created a book.
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
    private void doSave(@NonNull final AlertDialogListener nextStep) {
        Book book = getBook();

        // ask the currently displayed tab fragment to add it's fields; the others
        // did when they went in hiding
        //ENHANCE: alternative method: see SearchAdminActivity. Decide later
        DataEditor currentChildFragment = (DataEditor)
                getChildFragmentManager().findFragmentById(R.id.tab_fragment);
        //noinspection ConstantConditions
        currentChildFragment.saveFieldsTo(book);


        // Ignore validation failures; but we still validate to get the current values updated.
        book.validate();
//        if (!book.validate()) {
//              StandardDialogs.sendTaskUserMessage(this,
//               book.getValidationExceptionMessage(getResources()));
//        }

        // However, there is some data that we really do require...
        if (book.getList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty()) {
            UserMessage.showUserMessage(mActivity, R.string.warning_required_author_long);
            return;
        }
        if (!book.containsKey(UniqueId.KEY_TITLE) || book.getString(UniqueId.KEY_TITLE).isEmpty()) {
            UserMessage.showUserMessage(mActivity, R.string.warning_required_title);
            return;
        }

        if (book.getId() == 0) {
            String isbn = book.getString(UniqueId.KEY_BOOK_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && ((mDb.getBookIdFromIsbn(isbn, true) > 0))) {
                StandardDialogs.confirmSaveDuplicateBook(requireContext(), nextStep);
                return;
            }
        }

        // No special actions required...just do it.
        nextStep.onPositiveButton();
    }

    private void doCancel() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, getBook().getId());
        //ENHANCE: global changes not detected, so assume they happened.
        mActivity.setResult(Activity.RESULT_OK, data);
        mActivity.finishIfClean();
    }

    /**
     * Save the collected book details.
     */
    private void saveBook() {
        Book book = getBook();
        if (book.getId() == 0) {
            long id = mDb.insertBook(book);
            if (id > 0) {
                // if we got a cover while searching the internet, make it permanent
                if (book.getBoolean(UniqueId.BKEY_THUMBNAIL)) {
                    String uuid = mDb.getBookUuid(id);
                    // get the temporary downloaded file
                    File source = StorageUtils.getTempCoverFile();
                    File destination = StorageUtils.getCoverFile(uuid);
                    // and rename it to the permanent UUID one.
                    StorageUtils.renameFile(source, destination);
                }
            }
        } else {
            mDb.updateBook(book.getId(), book, 0);
        }
    }

    private class FragmentHolder {

        @NonNull
        final String tag;
        Fragment fragment;

        FragmentHolder(@NonNull final String tag) {
            this.tag = tag;
            fragment = getChildFragmentManager().findFragmentByTag(tag);
            if (fragment == null) {
                if (EditBookFieldsFragment.TAG.equals(tag)) {
                    fragment = new EditBookFieldsFragment();

                } else if (EditBookNotesFragment.TAG.equals(tag)) {
                    fragment = new EditBookNotesFragment();

                } else if (EditBookTOCFragment.TAG.equals(tag)) {
                    fragment = new EditBookTOCFragment();

                }
            }
        }
    }
}

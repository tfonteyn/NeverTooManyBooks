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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.baseactivity.CanBeDirty;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import org.jsoup.Connection;

import java.io.File;
import java.util.List;

/**
 * A tab host activity which holds the edit book tabs
 * 1. Details
 * 2. Notes
 * 3. Anthology titles
 * 4. Loan Book -> ENHANCE: remove this from this activity into either its own, or into a DialogFragment
 */
public class EditBookFragment extends BookAbstractFragment implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener,
        TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener,
        BookManager {

    public static final String TAG = "EditBookFragment";

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK;
    public static final int RESULT_CHANGES_MADE = UniqueId.ACTIVITY_RESULT_CHANGES_MADE_EDIT_BOOK;
    /**
     * Tabs in order
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_LOANS = 2;
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    /** the one and only book we're editing */
    private Book mBook;

    /** cache our activity to avoid multiple requireActivity and casting */
    private BaseActivity mActivity;

    /** */
    private TabLayout mTabLayout;
    @Nullable
    private TabLayout.Tab mAnthologyTab;

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="BookManager interface">
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
    public void setBook(final @NonNull Book book) {
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

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);

        mActivity = (BaseActivity)context;
    }

//    @Override
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        boolean isExistingBook = (getBook().getBookId() > 0);
        initTabs(isExistingBook, savedInstanceState);

        //noinspection ConstantConditions
        Button confirmButton = getView().findViewById(R.id.confirm);
        confirmButton.setText(isExistingBook ? R.string.btn_confirm_save_book : R.string.btn_confirm_add_book);
        confirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveBook(new PostSaveAction());
            }
        });

        getView().findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Cleanup because we may have made global changes
                // 2018-11-02: no longer doing this explicitly now... sooner or later a purge will happen anyhow
//                mDb.purgeAuthors();
//                mDb.purgeSeries();
                mActivity.finishIfClean();
            }
        });
    }

    @Override
    protected void initFields() {
        super.initFields();
    }

//    @CallSuper
//    @Override
//    public void onResume() {
//        super.onResume();
//    }

    @Override
    protected void onLoadFieldsFromBook(@NonNull final Book book, final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    /**
     * initial setup for editing
     */
    private void initTabs(final boolean isExistingBook, final @Nullable Bundle savedInstanceState) {

        //noinspection ConstantConditions
        mTabLayout = getView().findViewById(R.id.tab_panel);

        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        FragmentHolder fragmentHolder;

        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new EditBookFieldsFragment();
        fragmentHolder.tag = EditBookFieldsFragment.TAG;
        TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.tab_lbl_details).setTag(fragmentHolder);
        mTabLayout.addTab(tab);

        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new EditBookNotesFragment();
        fragmentHolder.tag = EditBookNotesFragment.TAG;
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_notes).setTag(fragmentHolder);
        mTabLayout.addTab(tab);

        addTOCTab(getBook().getBoolean(Book.IS_ANTHOLOGY));

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
            public void onTabSelected(final @NonNull TabLayout.Tab tab) {
                FragmentHolder fragmentHolder = (FragmentHolder) tab.getTag();
                //noinspection ConstantConditions
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.tab_fragment, fragmentHolder.fragment, fragmentHolder.tag)
                        .commit();
            }

            @Override
            public void onTabUnselected(final @NonNull TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(final @NonNull TabLayout.Tab tab) {
            }
        });
    }

    /**
     * add or remove the anthology tab
     */
    public void addTOCTab(final boolean show) {
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
//    public void onPause() {
//        super.onPause();
//    }

    @Override
    @CallSuper
    protected void onSaveFieldsToBook(final @NonNull Book book) {
        super.onSaveFieldsToBook(book);
    }

    /**
     * the only thing on this level is the TAB we're on
     * TEST: does this work ? we don't have a onResume ?
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putInt(REQUEST_BKEY_TAB, mTabLayout.getSelectedTabPosition());
        super.onSaveInstanceState(outState);
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        // do nothing here. Child fragments will add their own menus
    }

    /**
     * Dispatch incoming result to the correct fragment.
     * Called from the hosting {@link EditBookActivity#onActivityResult(int, int, Intent)}
     */
    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: forwarding to fragment - requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        // current visible child.
        Fragment frag = getChildFragmentManager().findFragmentById(R.id.tab_fragment);
        frag.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Default result: the id of the edited book
     */
    protected void setActivityResult() {
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, getBook().getBookId());
//        mActivity.setResult(mActivity.changesMade() ? RESULT_CHANGES_MADE : Activity.RESULT_CANCELED, data); /* many places */
        //ENHANCE: global changes not detected, so assume they happened.
        mActivity.setResult(RESULT_CHANGES_MADE, data); /* many places */
    }

    /**
     * This will save a book into the database, by either updating or created a book.
     *
     * It will check if the book already exists (isbn search) if you are creating a book;
     * if so the user will be prompted to confirm.
     *
     * In all cases, once the book is added/created, or not, the appropriate method of the
     * passed nextStep parameter will be executed. Passing nextStep is necessary because
     * this method may return after displaying a dialogue.
     *
     * @param nextStep The next step to be executed on confirm/cancel.
     */
    private void saveBook(final @NonNull PostConfirmOrCancelAction nextStep) {
        Book book = getBook();

        // ask the currently displayed tab fragment to add it's fields; the others did when they went in hiding
        //ENHANCE: alternative method: see SearchAdminActivity. Decide later
        //noinspection ConstantConditions
        DataEditor currentChildFragment = (DataEditor) getChildFragmentManager().findFragmentById(R.id.tab_fragment);
        currentChildFragment.saveFieldsTo(book);


        // Ignore validation failures; but we still validate to get the current values.
        book.validate();
//        if (!book.validate()) {
//            StandardDialogs.showUserMessage(this, book.getValidationExceptionMessage(getResources()));
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
                /*
                 * If it exists, show a dialog and use it to perform the
                 * next action, according to the users choice.
                 */
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.warning_duplicate_book_message))
                        .setTitle(R.string.title_duplicate_book)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .create();

                dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                doSaveBook();
                                nextStep.onConfirm();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                nextStep.onCancel();
                            }
                        });
                dialog.show();
                return;
            }
        }

        // No special actions required...just do it.
        doSaveBook();
        nextStep.onConfirm();
    }

    /**
     * Save the collected book details
     */
    private void doSaveBook() {
        Book book = getBook();
        if (book.getBookId() == 0) {
            long id = mDb.insertBook(book);
            if (id > 0) {
                File thumb = StorageUtils.getTempCoverFile();
                File real = StorageUtils.getCoverFile(mDb.getBookUuid(id));
                StorageUtils.renameFile(thumb, real);
            }
        } else {
            mDb.updateBook(book.getBookId(), book, 0);
        }

        // certainly made changes to the Book. Might be redundant, but I'm paranoid.
        mActivity.setChangesMade(true);
    }

    @Override
    public void onPartialDatePickerSave(@NonNull final PartialDatePickerDialogFragment dialog,
                                        final int destinationFieldId,
                                        @Nullable final Integer year, @Nullable final Integer month, @Nullable final Integer day) {

    }

    @Override
    public void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                          final int destinationFieldId) {
    }

    @Override
    public void onTextFieldEditorSave(@NonNull final TextFieldEditorDialogFragment dialog,
                                      final int destinationFieldId,
                                      @NonNull final String newText) {

    }

    @Override
    public void onTextFieldEditorCancel(@NonNull final TextFieldEditorDialogFragment dialog, final int destinationFieldId) {
    }

    @Override
    public <T2> void onCheckListEditorSave(@NonNull final CheckListEditorDialogFragment dialog,
                                           final int destinationFieldId,
                                           @NonNull final List<CheckListItem<T2>> list) {

    }

    @Override
    public void onCheckListEditorCancel(@NonNull final CheckListEditorDialogFragment dialog,
                                        final int destinationFieldId) {
    }

    /**
     * TODO: nice idea, but underused.
     * Ideas:
     * name the methods:  onPositive/onNegative + add a onNeutral
     * and use this wherever AlertDialog is used
     */
    public interface PostConfirmOrCancelAction {
        void onConfirm();

        @SuppressWarnings("EmptyMethod")
        void onCancel();
    }

    private class FragmentHolder {
        Fragment fragment;
        String tag;
    }

    private class PostSaveAction implements PostConfirmOrCancelAction {

        public void onConfirm() {
            EditBookFragment.this.setActivityResult();
            mActivity.finish();
        }

        public void onCancel() {
            // Do nothing
        }
    }
}

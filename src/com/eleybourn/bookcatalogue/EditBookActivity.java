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
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A tab host activity which holds the edit book tabs
 * 1. Details
 * 2. Notes
 * 3. Anthology titles
 * 4. Loan Book -> ENHANCE: remove this from this activity into either its own, or into a DialogFragment
 *
 * @author Evan Leybourn
 */
public class EditBookActivity extends BookBaseActivity implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener,
        TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK;

    /**
     * Tabs in order, see {@link #mTabClasses}
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_LOANS = 2;
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    /**
     * Classes used for the Tabs (in order)
     */
    private static final Class[] mTabClasses = {
            EditBookFieldsFragment.class,
            EditBookNotesFragment.class,
            EditBookLoanedFragment.class,
            EditBookTOCFragment.class
    };


    private TabLayout mTabLayout;
    @Nullable
    private TabLayout.Tab mAnthologyTab;

    /**
     * Load with the provided book id. Also open to the provided tab.
     *
     * @param activity the caller
     * @param id       The id of the book to edit
     * @param tab      Which tab to open first
     */
    public static void startActivityForResult(final @NonNull Activity activity,
                                              final long id,
                                              final int tab) {
        Intent intent = new Intent(activity, EditBookActivity.class);
        intent.putExtra(UniqueId.KEY_ID, id);
        intent.putExtra(EditBookActivity.REQUEST_BKEY_TAB, tab);
        activity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_book_base;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);

        mDb = new CatalogueDBAdapter(this)
                .open();
        Bundle extras = getIntent().getExtras();

        long bookId = getLongFromBundles(UniqueId.KEY_ID, savedInstanceState, extras);
        mBook = loadBook(bookId, savedInstanceState == null ? extras : savedInstanceState);

        mTabLayout = findViewById(R.id.tab_panel);

        boolean isExistingBook = (bookId > 0);
        initTabs(extras, isExistingBook);
        initCancelConfirmButtons(isExistingBook);

        Tracker.exitOnCreate(this);
    }

    /**
     * initial setup for editing
     */
    private void initTabs(final @Nullable Bundle extras, final boolean isExistingBook) {

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
            tab = mTabLayout.newTab().setText(R.string.lbl_notes).setTag(holder);
            mTabLayout.addTab(tab);
            mAllTabs.add(tab);

            addTOCTab(mBook.getBoolean(Book.IS_ANTHOLOGY));

            // can't loan out a new book yet (or user does not like loaning)
            if (isExistingBook && Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO)) {
                holder = new Holder();
                holder.fragment = (Fragment) mTabClasses[TAB_EDIT_LOANS].newInstance();
                tab = mTabLayout.newTab().setText(R.string.lbl_loaned_to).setTag(holder);
                mTabLayout.addTab(tab);
                mAllTabs.add(tab);
            }
        } catch (@NonNull InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        // any specific tab desired as 'selected' ?
        int showTab = TAB_EDIT;
        if (extras != null && extras.containsKey(REQUEST_BKEY_TAB)) {
            int tabWanted = extras.getInt(REQUEST_BKEY_TAB);
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
                    throw new RTE.IllegalTypeException("unknown tab: " + tabWanted);
            }
        }

        TabLayout.Tab ourTab = mAllTabs.get(showTab);
        ourTab.select();
        //noinspection ConstantConditions
        Fragment frag = ((Holder) ourTab.getTag()).fragment;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, frag)
                .commit();

        // finally hook up our listener.
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(final @NonNull TabLayout.Tab tab) {
                //noinspection ConstantConditions
                Fragment frag = ((Holder) tab.getTag()).fragment;
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment, frag)
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

    private void initCancelConfirmButtons(final boolean isExistingBook) {
        Button mConfirmButton = findViewById(R.id.confirm);
        if (isExistingBook) {
            mConfirmButton.setText(R.string.btn_confirm_save_book);
        } else {
            mConfirmButton.setText(R.string.btn_confirm_add_book);
        }

        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveBook(new PostSaveAction());
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Cleanup because we may have made global changes
                // 2018-11-02: no longer doing this explicitly now... sooner or later a purge will happen anyhow
//                mDb.purgeAuthors();
//                mDb.purgeSeries();
                // cancel == going 'up' => call onBackPressed as that will check the isDirty() status with a dialog
                onBackPressed();
            }
        });
    }

    /**
     * If it's a new book, then we'll load all from the database again.
     *
     * @param bookId to retrieve
     */
    @Override
    public void reload(final long bookId) {
        // only reload if it's a new book
        if (bookId != mBook.getBookId()) {
            super.reload(bookId);
        }
    }

    /**
     * add or remove the anthology tab
     */
    public void addTOCTab(final boolean show) {
        if (show) {
            if (mAnthologyTab == null) {
                Holder holder = new Holder();
                try {
                    holder.fragment = (Fragment) mTabClasses[TAB_EDIT_ANTHOLOGY].newInstance();
                    mAnthologyTab = mTabLayout.newTab()
                            .setText(R.string.content_tab)
                            .setTag(holder);
                } catch (@NonNull InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
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
        DataEditor dataEditorFragment = (DataEditor) getSupportFragmentManager().findFragmentById(R.id.fragment);
        dataEditorFragment.saveTo(mBook);

        // Ignore validation failures; but we still validate to get the current values.
        mBook.validate();
//        if (!mBook.validate()) {
//            StandardDialogs.showUserMessage(this, mBook.getValidationExceptionMessage(getResources()));
//        }

        // However, there is some data that we really do require...
        if (mBook.getAuthorList().size() == 0) {
            StandardDialogs.showUserMessage(this, R.string.warning_required_author);
            return;
        }
        if (!mBook.containsKey(UniqueId.KEY_TITLE) || mBook.getString(UniqueId.KEY_TITLE).isEmpty()) {
            StandardDialogs.showUserMessage(this, R.string.warning_required_title);
            return;
        }

        if (mBook.getBookId() == 0) {
            String isbn = mBook.getString(UniqueId.KEY_BOOK_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && ((mDb.getIdFromIsbn(isbn, true) > 0))) {
                /*
                 * If it exists, show a dialog and use it to perform the
                 * next action, according to the users choice.
                 */
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.duplicate_book_message))
                        .setTitle(R.string.dialog_title_duplicate_book)
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
        if (mBook.getBookId() == 0) {
            long id = mDb.insertBook(mBook);
            if (id > 0) {
                File thumb = StorageUtils.getTempCoverFile();
                File real = StorageUtils.getCoverFile(mDb.getBookUuid(id));
                StorageUtils.renameFile(thumb, real);
            }
        } else {
            mDb.updateBook(mBook.getBookId(), mBook, 0);
        }
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);

        outState.putLong(UniqueId.KEY_ID, mBook.getBookId());
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, mBook.getRawData());
        outState.putInt(EditBookActivity.REQUEST_BKEY_TAB, mTabLayout.getSelectedTabPosition());

        super.onSaveInstanceState(outState);
        Tracker.exitOnSaveInstanceState(this);
    }

    /**
     * setResult with the correct data set for this activity
     */
    @Override
    protected void setActivityResult() {
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, mBook.getBookId());
        // TODO: detect POTENTIAL global changes, and if none, return Activity.RESULT_CANCELLED instead
        setResult(Activity.RESULT_OK, data); /* many places */
    }

    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public <T> void onCheckListEditorSave(final @NonNull CheckListEditorDialogFragment dialog,
                                          final int destinationFieldId,
                                          final @NonNull List<CheckListItem<T>> list) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof CheckListEditorDialogFragment.OnCheckListEditorResultsListener) {
            ((CheckListEditorDialogFragment.OnCheckListEditorResultsListener) frag)
                    .onCheckListEditorSave(dialog, destinationFieldId, list);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onCheckListEditorSave result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public void onCheckListEditorCancel(final @NonNull CheckListEditorDialogFragment dialog,
                                        final int destinationFieldId) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof CheckListEditorDialogFragment.OnCheckListEditorResultsListener) {
            ((CheckListEditorDialogFragment.OnCheckListEditorResultsListener) frag)
                    .onCheckListEditorCancel(dialog, destinationFieldId);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onCheckListEditorCancel result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public void onPartialDatePickerSave(final @NonNull PartialDatePickerDialogFragment dialog,
                                             final @IdRes int destinationFieldId,
                                             final @Nullable Integer year,
                                             final @Nullable Integer month,
                                             final @Nullable Integer day) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener) {
            ((PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener) frag)
                    .onPartialDatePickerSave(dialog, destinationFieldId, year, month, day);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onPartialDatePickerSave result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public void onPartialDatePickerCancel(final @NonNull PartialDatePickerDialogFragment dialog,
                                          final @IdRes int destinationFieldId) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener) {
            ((PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener) frag)
                    .onPartialDatePickerCancel(dialog, destinationFieldId);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onPartialDatePickerCancel result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }
    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public void onTextFieldEditorSave(final @NonNull TextFieldEditorDialogFragment dialog,
                                      final int destinationFieldId,
                                      final @NonNull String newText) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener) {
            ((TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener) frag)
                    .onTextFieldEditorSave(dialog, destinationFieldId, newText);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onTextFieldEditorSave result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    /**
     * Dialog handler; pass results to relevant fragment
     */
    @Override
    public void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialogFragment dialog,
                                        final int destinationFieldId) {

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener) {
            ((TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener) frag)
                    .onTextFieldEditorCancel(dialog, destinationFieldId);
        } else {
            StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
            Logger.error("Received onTextFieldEditorCancel result with no fragment to handle it");
        }
        // Make sure it's dismissed
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
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

    private class Holder {
        Fragment fragment;
    }


    private class PostSaveAction implements PostConfirmOrCancelAction {

        public void onConfirm() {
            setActivityResult();
            finish();
        }

        public void onCancel() {
            // Do nothing
        }
    }
}

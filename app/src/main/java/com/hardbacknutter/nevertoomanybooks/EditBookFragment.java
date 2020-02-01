/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * Fragment that hosts child fragments to edit a book.
 * <p>
 * Does not have fields of its own, as it's merely the coordinator/container for the actual
 * editor fragments.
 */
public class EditBookFragment
        extends Fragment {

    public static final String TAG = "EditBookFragment";

    /** Which tab to open in {@link #onResume()}. */
    private static final String BKEY_TAB = TAG + ":tab";
    private final List<Class> mTabClasses = new ArrayList<>();
    private final List<Integer> mTabTitles = new ArrayList<>();
    private ViewPager2 mViewPager;
    private TabAdapter mTabAdapter;
    private TabLayoutMediator mTabLayoutMediator;

    /** The book. Must be in the Activity scope. */
    private BookBaseFragmentModel mBookModel;
    private int mInitialTab = 0;

    private TabLayout mTabLayout;

    static boolean showAuthSeriesOnTabs(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_edit_book_tabs_authSer, false);
    }

    static boolean showTabNativeId(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_edit_book_tabs_native_id, false);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book, container, false);
        mViewPager = view.findViewById(R.id.tab_fragment);
        return view;
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mBookModel = new ViewModelProvider(getActivity()).get(BookBaseFragmentModel.class);
        mBookModel.init(getArguments());

        // any specific tab desired as 'selected' ?
        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : getArguments();
        if (currentArgs != null) {
            mInitialTab = currentArgs.getInt(BKEY_TAB, 0);
        }

        mTabAdapter = new TabAdapter(this, mTabClasses);
        mViewPager.setAdapter(mTabAdapter);

        // The tab bar lives in the activity layout inside the AppBarLayout!
        mTabLayout = getActivity().findViewById(R.id.tab_panel);

        // The FAB lives in the activity.
        FloatingActionButton fabButton = getActivity().findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_save);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> prepareSave(true));
    }

    @Override
    public void onResume() {
        super.onResume();

        // create the list depending on user preferences
        initTabList();
        mTabAdapter.notifyDataSetChanged();

        // detach any existing mediator.
        if (mTabLayoutMediator != null) {
            mTabLayoutMediator.detach();
        }

        // Creating a new mediator in onResume seems mandatory. Not doing this causes issues
        // if the settings (for showing tabs/popup screens) are changed.
        mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mViewPager, (tab, position) ->
                tab.setText(getString(mTabTitles.get(position))));
        mTabLayoutMediator.attach();

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mViewPager.setOffscreenPageLimit(mTabClasses.size());

        if (mInitialTab >= mTabClasses.size()) {
            mInitialTab = 0;
        }
        mViewPager.setCurrentItem(mInitialTab);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_TAB, mViewPager.getCurrentItem());
    }

    /**
     * Build the tab classes and title list arrays.
     */
    private void initTabList() {
        mTabClasses.clear();
        mTabTitles.clear();

        mTabClasses.add(EditBookFieldsFragment.class);
        mTabTitles.add(R.string.tab_lbl_details);

        //noinspection ConstantConditions
        if (showAuthSeriesOnTabs(getContext())) {
            mTabClasses.add(EditBookAuthorsFragment.class);
            mTabTitles.add(R.string.lbl_authors);

            if (App.isUsed(DBDefinitions.KEY_FK_SERIES)) {
                mTabClasses.add(EditBookSeriesFragment.class);
                mTabTitles.add(R.string.lbl_series_multiple);
            }
        }

        mTabClasses.add(EditBookPublicationFragment.class);
        mTabTitles.add(R.string.tab_lbl_publication);

        mTabClasses.add(EditBookNotesFragment.class);
        mTabTitles.add(R.string.tab_lbl_notes);

        if (App.isUsed(DBDefinitions.KEY_TOC_BITMASK)) {
            mTabClasses.add(EditBookTocFragment.class);
            mTabTitles.add(R.string.tab_lbl_content);
        }

        if (showTabNativeId(getContext())) {
            mTabClasses.add(EditBookNativeIdFragment.class);
            mTabTitles.add(R.string.tab_lbl_ext_id);
        }
    }

    /**
     * Called when the user clicks 'save'.
     * <p>
     * Validates the data.<br>
     * Checks if the book already exists (isbn search) when the user is creating a book;
     * if so we prompt to confirm.
     *
     * <strong>Dev. note:</strong> we explicitly check for isResumed() fragments.
     * For now, there will only ever be a single (front/visible), but this code
     * should be able to cope with future layouts showing multiple fragments at once (flw)
     *
     * @param checkUnfinishedEdits Initially {@code true}. Will be {@code true} when called
     *                             when the user clicks on "save" when prompted there are
     *                             unfinished edits.
     */
    private void prepareSave(final boolean checkUnfinishedEdits) {

        Book book = mBookModel.getBook();

        //noinspection ConstantConditions
        EditBookBaseFragment.UnfinishedEdits unfinishedEdits =
                new ViewModelProvider(getActivity())
                        .get(EditBookBaseFragment.UnfinishedEdits.class);

        // The ViewPager2 fragments are created as children.
        List<Fragment> fragments = getChildFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            Fragment frag = fragments.get(i);

            // 1. Fragments which went through onPause (i.e. are NOT resumed)
            // These have saved their *confirmed* data to the book, but might have unfinished data
            // as indicated in EditBookBaseFragment.UnfinishedEdits
            if (checkUnfinishedEdits
                && unfinishedEdits.fragments.contains(frag.getTag())
                && !frag.isResumed()) {
                // bring it to the front; i.e. resume it; the user will see it below the dialog.
                mViewPager.setCurrentItem(i);
                //noinspection ConstantConditions
                StandardDialogs.unsavedEditsDialog(
                        getContext(),
                        () -> prepareSave(false),
                        () -> ((EditBookActivity) getActivity())
                                .cleanupAndSetResults(mBookModel, true));
                return;
            }

            // 2. Fragments currently in resumed state (i.e. visible/active)
            // We need to explicitly tell them to save their data, and manually check
            // for unfinished edits (basically mimic their onPause)
            if (frag instanceof DataEditor && frag.isResumed()) {
                //noinspection unchecked
                DataEditor<Book> dataEditor = ((DataEditor<Book>) frag);
                dataEditor.onSaveFields(book);
                if (dataEditor.hasUnfinishedEdits() && checkUnfinishedEdits) {
                    mViewPager.setCurrentItem(i);
                    //noinspection ConstantConditions
                    StandardDialogs.unsavedEditsDialog(
                            getContext(),
                            () -> prepareSave(false),
                            () -> ((EditBookActivity) getActivity())
                                    .cleanupAndSetResults(mBookModel, true));
                    return;
                }
            }
        }

        // if we're NOT running in tabbed mode for authors/series, send them a save command.
        //noinspection ConstantConditions
        if (!showAuthSeriesOnTabs(getContext())) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            // Only resumed fragments (i.e. front/visible) can/will be asked to save their state.
            String[] fragmentTags = {EditBookAuthorsFragment.TAG, EditBookSeriesFragment.TAG};
            for (String tag : fragmentTags) {
                Fragment frag = fm.findFragmentByTag(tag);
                if (frag instanceof DataEditor && frag.isResumed()) {
                    //noinspection unchecked
                    DataEditor<Book> dataEditor = ((DataEditor<Book>) frag);
                    dataEditor.onSaveFields(book);
                    if (dataEditor.hasUnfinishedEdits() && checkUnfinishedEdits) {
                        StandardDialogs.unsavedEditsDialog(
                                getContext(),
                                () -> prepareSave(false),
                                () -> ((EditBookActivity) getActivity())
                                        .cleanupAndSetResults(mBookModel, true));
                        return;
                    }
                }
            }
        }

        // Now validate the book data
        if (!book.validate(getContext())) {
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.vldt_failure)
                    .setMessage(book.getValidationExceptionMessage(getContext()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        // If this is a new Book, check if it already exists based on ISBN
        if (book.isNew()) {
            String isbnStr = book.getString(DBDefinitions.KEY_ISBN);
            if (!isbnStr.isEmpty()) {
                ISBN isbn = ISBN.createISBN(isbnStr);
                if (mBookModel.getDb().getBookIdFromIsbn(isbn) > 0) {
                    new AlertDialog.Builder(getContext())
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setTitle(R.string.title_duplicate_book)
                            .setMessage(R.string.confirm_duplicate_book_message)
                            // this dialog is important. Make sure the user pays some attention
                            .setCancelable(false)
                            // User aborts this edit
                            .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                                    getActivity().finish())
                            // User wants to continue editing this book
                            .setNeutralButton(R.string.edit, (dialog, which) ->
                                    dialog.dismiss())
                            // User wants to add regardless
                            .setPositiveButton(R.string.btn_confirm_add, (dialog, which) ->
                                    saveBook())
                            .create()
                            .show();
                    return;
                }
            }
        }

        // No special actions required...just do it.
        saveBook();
    }

    /**
     * Save the collected book details.
     */
    private void saveBook() {
        //noinspection ConstantConditions
        if (mBookModel.saveBook(getContext())) {
            //noinspection ConstantConditions
            getActivity().setResult(Activity.RESULT_OK, mBookModel.getResultData());
            getActivity().finish();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_unexpected_error, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        @NonNull
        private final List<Class> mTabClasses;

        /**
         * Constructor.
         *
         * @param container  hosting fragment
         * @param tabClasses for the fragments
         */
        TabAdapter(@NonNull final Fragment container,
                   @NonNull final List<Class> tabClasses) {
            super(container);
            mTabClasses = tabClasses;
        }

        @Override
        public int getItemCount() {
            return mTabClasses.size();
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            try {
                return (Fragment) mTabClasses.get(position).newInstance();
            } catch (@NonNull final IllegalAccessException
                    | java.lang.InstantiationException ignore) {
                // ignore
            }
            // We'll never get here...
            //noinspection ConstantConditions
            return null;
        }
    }
}

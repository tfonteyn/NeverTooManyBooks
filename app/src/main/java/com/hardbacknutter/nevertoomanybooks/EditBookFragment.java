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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Fragment that hosts child fragments to edit a book.
 * <p>
 * Does not have fields of its own, as it's merely the coordinator/container for the actual
 * editor fragments.
 * <p>
 * TODO: eliminate... move functionality to the activity and ViewModel
 */
public class EditBookFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "EditBookFragment";
    private static final String BKEY_TAB = TAG + ":tab";

    private ViewPager2 mViewPager;
    private TabAdapter mViewPagerAdapter;
    @SuppressWarnings("FieldCanBeLocal")
    private TabLayoutMediator mTabLayoutMediator;
    @SuppressWarnings("FieldCanBeLocal")
    private TabLayout mTabLayout;

    /** The book. Must be in the Activity scope. */
    private BookViewModel mBookViewModel;

    /** Track the currently displayed tab so we can survive recreations. {@link #BKEY_TAB}. */
    private int mCurrentTab;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_edit_book, container, false);
        mViewPager = view.findViewById(R.id.tab_fragment);
        return view;
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(BKEY_TAB);
        }
        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
        //noinspection ConstantConditions
        mBookViewModel.init(getContext(), getArguments(), true);

        // The tab bar lives in the activity layout inside the AppBarLayout!
        mTabLayout = getActivity().findViewById(R.id.tab_panel);

        mViewPagerAdapter = new TabAdapter(this);
        mViewPager.setAdapter(mViewPagerAdapter);

        mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mViewPager, (tab, position) ->
                tab.setText(getString(mViewPagerAdapter.getTabTitle(position))));
        mTabLayoutMediator.attach();

        // The FAB lives in the activity.
        final FloatingActionButton fabButton = getActivity().findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_save);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> prepareSave(true));
    }

    @Override
    public void onResume() {
        super.onResume();

        // sanity check
        if (mCurrentTab >= mViewPagerAdapter.getItemCount()) {
            mCurrentTab = 0;
        }
        mViewPager.setCurrentItem(mCurrentTab);

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mViewPager.setOffscreenPageLimit(mViewPagerAdapter.getItemCount());
    }

    @Override
    public void onPause() {
        super.onPause();
        mCurrentTab = mViewPager.getCurrentItem();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_TAB, mCurrentTab);
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
        final Book book = mBookViewModel.getBook();
        final Collection<String> unfinishedEdits = mBookViewModel.getUnfinishedEdits();

        // The ViewPager2 fragments are created as children.
        final List<Fragment> fragments = getChildFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            final Fragment frag = fragments.get(i);

            // 1. Fragments which went through onPause (i.e. are NOT resumed)
            // These have saved their *confirmed* data to the book, but might have unfinished data
            // as indicated in EditBookBaseFragment.UnfinishedEdits
            if (checkUnfinishedEdits && unfinishedEdits.contains(frag.getTag())
                && !frag.isResumed()) {
                // bring it to the front; i.e. resume it; the user will see it below the dialog.
                mViewPager.setCurrentItem(i);
                //noinspection ConstantConditions
                StandardDialogs.unsavedEdits(
                        getContext(),
                        () -> prepareSave(false),
                        () -> ((EditBookActivity) getActivity())
                                .cleanupAndSetResults(mBookViewModel, true));
                return;
            }

            // 2. Fragments currently in resumed state (i.e. visible/active)
            // We need to explicitly tell them to save their data, and manually check
            // for unfinished edits (basically mimic their onPause)
            if (frag instanceof DataEditor && frag.isResumed()) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) frag;
                dataEditor.onSaveFields(book);
                if (dataEditor.hasUnfinishedEdits() && checkUnfinishedEdits) {
                    mViewPager.setCurrentItem(i);
                    //noinspection ConstantConditions
                    StandardDialogs.unsavedEdits(
                            getContext(),
                            () -> prepareSave(false),
                            () -> ((EditBookActivity) getActivity())
                                    .cleanupAndSetResults(mBookViewModel, true));
                    return;
                }
            }
        }

        // if we're NOT running in tabbed mode for authors/series, send them a save command.
        //noinspection ConstantConditions
        if (!Prefs.showAuthSeriesOnTabs(getContext())) {
            //noinspection ConstantConditions
            final FragmentManager fm = getActivity().getSupportFragmentManager();
            // Only resumed fragments (i.e. front/visible) can/will be asked to save their state.
            final String[] fragmentTags = {EditBookAuthorsFragment.TAG, EditBookSeriesFragment.TAG};
            for (String tag : fragmentTags) {
                final Fragment frag = fm.findFragmentByTag(tag);
                if (frag instanceof DataEditor && frag.isResumed()) {
                    //noinspection unchecked
                    final DataEditor<Book> dataEditor = (DataEditor<Book>) frag;
                    dataEditor.onSaveFields(book);
                    if (dataEditor.hasUnfinishedEdits() && checkUnfinishedEdits) {
                        StandardDialogs.unsavedEdits(
                                getContext(),
                                () -> prepareSave(false),
                                () -> ((EditBookActivity) getActivity())
                                        .cleanupAndSetResults(mBookViewModel, true));
                        return;
                    }
                }
            }
        }

        // Now validate the book data
        if (!book.validate(getContext())) {
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.vldt_failure)
                    .setMessage(book.getValidationExceptionMessage(getContext()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        // Check if the book already exists
        if (mBookViewModel.bookExists()) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(R.string.confirm_duplicate_book_message)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this edit
                    .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                    // User wants to continue editing this book
                    .setNeutralButton(R.string.action_edit, (d, w) -> d.dismiss())
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> saveBook())
                    .create()
                    .show();
            return;
        }

        // No special actions required...just do it.
        saveBook();
    }

    /**
     * Save the collected book details.
     */
    private void saveBook() {
        try {
            //noinspection ConstantConditions
            mBookViewModel.saveBook(getContext());
            //noinspection ConstantConditions
            getActivity().setResult(Activity.RESULT_OK, mBookViewModel.getResultData());
            getActivity().finish();
        } catch (@NonNull final DAO.DaoWriteException e) {
            Logger.error(getContext(), TAG, e);
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_unexpected_error, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        /** Visible tabs as per user preferences. */
        private final List<TabInfo> mTabs = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param container hosting fragment
         */
        TabAdapter(@NonNull final Fragment container) {
            super(container);

            final Context context = container.getContext();
            //noinspection ConstantConditions
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            // Build the tab classes and title list arrays.
            mTabs.add(new TabInfo(EditBookFieldsFragment.class, R.string.tab_lbl_details));

            if (Prefs.showAuthSeriesOnTabs(context)) {
                mTabs.add(new TabInfo(EditBookAuthorsFragment.class,
                                      R.string.lbl_authors));
                if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SERIES_TITLE)
                    && Prefs.showAuthSeriesOnTabs(context)) {
                    mTabs.add(new TabInfo(EditBookSeriesFragment.class,
                                          R.string.lbl_series_multiple));
                }
            }

            mTabs.add(new TabInfo(EditBookPublicationFragment.class, R.string.tab_lbl_publication));
            mTabs.add(new TabInfo(EditBookNotesFragment.class, R.string.tab_lbl_notes));

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
                mTabs.add(new TabInfo(EditBookTocFragment.class,
                                      R.string.tab_lbl_content));
            }
            if (Prefs.showTabNativeId(context)) {
                mTabs.add(new TabInfo(EditBookNativeIdFragment.class,
                                      R.string.tab_lbl_ext_id));
            }
        }

        @StringRes
        int getTabTitle(final int position) {
            return mTabs.get(position).titleId;
        }

        @Override
        public int getItemCount() {
            return mTabs.size();
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            try {
                return (Fragment) mTabs.get(position).clazz.newInstance();

            } catch (@NonNull final IllegalAccessException
                    | java.lang.InstantiationException ignore) {
                // ignore
            }
            // We'll never get here...
            //noinspection ConstantConditions
            return null;
        }

        /** Value class to match up a tab fragment class and the title to use for the tab. */
        private static class TabInfo {

            @NonNull
            final Class clazz;
            @StringRes
            final int titleId;

            TabInfo(@NonNull final Class clazz,
                    final int titleId) {
                this.clazz = clazz;
                this.titleId = titleId;
            }
        }
    }
}

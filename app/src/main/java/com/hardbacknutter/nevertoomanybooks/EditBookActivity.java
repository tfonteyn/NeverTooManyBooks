/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityEditBookBinding;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The hosting activity for editing a book.
 */
public class EditBookActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "EditBookActivity";
    /** Currently displayed tab. */
    private static final String BKEY_TAB = TAG + ":tab";

    /** Host for the tabbed fragments. */
    private TabAdapter mViewPagerAdapter;

    /** The book. Must be in the Activity scope. */
    private BookViewModel mBookViewModel;

    /** Track the currently displayed tab so we can survive recreations. {@link #BKEY_TAB}. */
    private int mCurrentTab;
    /** View binding. */
    private ActivityEditBookBinding mVb;

    @Override
    protected void onSetContentView() {
        mVb = ActivityEditBookBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(BKEY_TAB);
        }

        mBookViewModel = new ViewModelProvider(this).get(BookViewModel.class);
        mBookViewModel.init(this, getIntent().getExtras());
        mBookViewModel.enableValidators();

        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);

        mViewPagerAdapter = new TabAdapter(this);
        mVb.pager.setAdapter(mViewPagerAdapter);

        new TabLayoutMediator(mVb.tabPanel, mVb.pager, (tab, position) ->
                tab.setText(getString(mViewPagerAdapter.getTabTitle(position))))
                .attach();
    }

    @Override
    public void onResume() {
        super.onResume();
        // sanity check
        if (mCurrentTab >= mViewPagerAdapter.getItemCount()) {
            mCurrentTab = 0;
        }
        mVb.pager.setCurrentItem(mCurrentTab);

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mVb.pager.setOffscreenPageLimit(mViewPagerAdapter.getItemCount());
    }

    @Override
    public void onPause() {
        super.onPause();
        mCurrentTab = mVb.pager.getCurrentItem();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_TAB, mCurrentTab);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        // Settings initiated from the navigation panel.
        if (requestCode == RequestCode.NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // update the search sites list.
                // ArrayList<Site> sites = data.getParcelableArrayListExtra(
                //                                      Site.Type.Data.getBundleKey());
                // if (sites != null) {
                //     SearchCoordinator model =
                //             new ViewModelProvider(this).get(SearchCoordinator.class);
                //     model.setSiteList(sites);
                // }

                // Reset the scanner if it was changed.
                // Note this creates the scanner model even if it did not exist before.
                // Other than using memory, this is fine.
                // We assume if the user explicitly went to settings to change the scanner
                // they want to use it.
                if (data.getBooleanExtra(BarcodePreferenceFragment.BKEY_SCANNER_MODIFIED, false)) {
                    final ScannerViewModel model =
                            new ViewModelProvider(this).get(ScannerViewModel.class);
                    model.resetScanner();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {

        final FragmentManager fm = getSupportFragmentManager();
        //TODO: this might no longer be needed since we use DialogFragment for authors/series
        final int backStackEntryCount = fm.getBackStackEntryCount();

        // 1. Check for the current (i.e. in resumed state) fragment having unfinished edits
        if (backStackEntryCount > 0) {
            final String tag = fm.getBackStackEntryAt(backStackEntryCount - 1).getName();
            final Fragment frag = fm.findFragmentByTag(tag);
            if (frag instanceof DataEditor && frag.isResumed()) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) frag;
                if (dataEditor.hasUnfinishedEdits()) {
                    StandardDialogs.unsavedEdits(this, null, super::onBackPressed);
                    return;
                }
            }
        }

        // 2. If we're at the top level, check if the book was changed.
        if (backStackEntryCount == 0 && mBookViewModel.isDirty()) {
            StandardDialogs.unsavedEdits(this,
                                         () -> prepareSave(true),
                                         this::setResultsAndFinish);
            return;
        }

        // Once here, we have no unfinished edits; and if we're on the top level,
        // the book data was saved (or never changed)
        if (backStackEntryCount == 0) {
            setResultsAndFinish();
        }

        super.onBackPressed();
    }

    /**
     * Prepare data for saving.
     *
     * <ol>
     *     <li>Check all fragments for having properly saved their data</li>
     *     <li>Validate the data</li>
     *     <li>Check if the book already exists</li>
     *     <li>If all is fine, calls {@link #saveBook()}</li>
     * </ol>
     *
     * @param checkUnfinishedEdits Should be {@code true} for the initial call.
     *                             If there are unfinished edits, and the user clicks on
     *                             "save" when prompted, this method will call itself
     *                             with {@code false}
     */
    public void prepareSave(final boolean checkUnfinishedEdits) {
        final Book book = mBookViewModel.getBook();
        // this will normally only contain 1 element maximum but is designed (and proven
        // to work) with multiple.
        final Collection<String> unfinishedEdits = mBookViewModel.getUnfinishedEdits();

        final List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            final Fragment fragment = fragments.get(i);
            // Nor really needed to check for being a DataEditor,
            // but this leave the possibility to add non-DataEditor fragments.
            if (fragment instanceof DataEditor) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) fragment;

                // 1. Fragments which went through onPause (i.e. are NOT resumed)
                // have saved their *confirmed* data, but might have unfinished edits
                // as previously logged in mBookViewModel.getUnfinishedEdits()
                if (!dataEditor.isResumed()
                    && checkUnfinishedEdits
                    && unfinishedEdits.contains(dataEditor.getFragmentId())) {
                    // bring it to the front; i.e. resume it; the user will see it below the dialog.
                    mVb.pager.setCurrentItem(i);
                    StandardDialogs.unsavedEdits(this,
                                                 () -> prepareSave(false),
                                                 this::setResultsAndFinish);
                    return;
                }

                // 2. Fragments in resumed state (i.e. NOT gone through onPause) must be
                // explicitly told to save their data, and we must manually check them
                // for unfinished edits with a direct call to dataEditor.hasUnfinishedEdits()
                // Note that for now, there will only ever be a single (front/visible),
                // but this code should be able to cope with future layouts showing
                // multiple fragments at once (flw)
                if (dataEditor.isResumed()) {
                    dataEditor.onSaveFields(book);
                    if (checkUnfinishedEdits && dataEditor.hasUnfinishedEdits()) {
                        mVb.pager.setCurrentItem(i);
                        StandardDialogs.unsavedEdits(this,
                                                     () -> prepareSave(false),
                                                     this::setResultsAndFinish);
                        return;
                    }
                }
            }
        }

        // Now validate the book data
        if (!book.validate(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle(R.string.vldt_failure)
                    .setMessage(book.getValidationExceptionMessage())
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        // Check if the book already exists
        if (mBookViewModel.bookExists()) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(R.string.confirm_duplicate_book_message)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> setResultsAndFinish())
                    .setNeutralButton(R.string.action_edit, (d, w) -> d.dismiss())
                    // add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> saveBook())
                    .create()
                    .show();
            return;
        }

        // All ready, go for it!
        saveBook();
    }

    /**
     * Save the collected book details.
     */
    void saveBook() {
        try {
            mBookViewModel.saveBook(this);
            setResultsAndFinish();

        } catch (@NonNull final DAO.DaoWriteException | IOException e) {
            Logger.error(this, TAG, e);
            StandardDialogs.showError(this, R.string.error_storage_not_writable);
        }
    }

    void setResultsAndFinish() {
        // The result data will contain the re-position book id.
        setResult(Activity.RESULT_OK, mBookViewModel.getResultIntent());
        finish();
    }

    @Override
    protected void onDestroy() {
        CoverHandler.deleteOrphanedCoverFiles(this);
        super.onDestroy();
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        /** Visible tabs as per user preferences. */
        private final List<TabInfo> mTabs = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param activity hosting fragment
         */
        TabAdapter(@NonNull final FragmentActivity activity) {
            super(activity);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            // Build the tab class/title list.
            mTabs.add(new TabInfo(EditBookFieldsFragment.class, R.string.tab_lbl_details));
            mTabs.add(new TabInfo(EditBookPublicationFragment.class, R.string.tab_lbl_publication));
            mTabs.add(new TabInfo(EditBookNotesFragment.class, R.string.tab_lbl_notes));

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
                mTabs.add(new TabInfo(EditBookTocFragment.class, R.string.tab_lbl_content));
            }
            if (Prefs.showEditBookTabExternalId(prefs)) {
                mTabs.add(new TabInfo(EditBookExternalIdFragment.class, R.string.tab_lbl_ext_id));
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
                return mTabs.get(position).clazz.newInstance();

            } catch (@NonNull final IllegalAccessException | InstantiationException e) {
                // We'll never get here...
                throw new IllegalStateException(e);
            }
        }

        /** Value class to match up a tab fragment class and the title to use for the tab. */
        private static class TabInfo {

            @NonNull
            final Class<? extends Fragment> clazz;
            @StringRes
            final int titleId;

            TabInfo(@NonNull final Class<? extends Fragment> clazz,
                    @StringRes final int titleId) {
                this.clazz = clazz;
                this.titleId = titleId;
            }
        }
    }
}

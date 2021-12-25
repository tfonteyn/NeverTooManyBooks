/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookBinding;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

public class EditBookFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "EditBookActivity";

    /** Host for the tabbed fragments. */
    private TabAdapter mTabAdapter;
    /** View model. Must be in the Activity scope. */
    private EditBookViewModel mVm;
    /** View Binding. */
    private FragmentEditBookBinding mVb;

    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Warn the user if the book was changed
                    if (mVm.getBook().getStage() == EntityStage.Stage.Dirty) {
                        //noinspection ConstantConditions
                        StandardDialogs.unsavedEdits(getContext(),
                                                     () -> prepareSave(true),
                                                     () -> setResultsAndFinish());
                        return;
                    }

                    setResultsAndFinish();
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mTabAdapter = new TabAdapter(getActivity());
        mVb.pager.setAdapter(mTabAdapter);

        final TabLayout tabPanel = getActivity().findViewById(R.id.tab_panel);

        new TabLayoutMediator(tabPanel, mVb.pager, (tab, position) -> {
            final TabAdapter.TabInfo tabInfo = mTabAdapter.getTabInfo(position);
            tab.setText(tabInfo.titleId);
            tab.setContentDescription(tabInfo.contentDescriptionId);
        }).attach();

    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_action_save, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));
    }

    @CallSuper
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            prepareSave(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        int currentTab = mVm.getCurrentTab();
        // sanity check
        if (currentTab >= mTabAdapter.getItemCount()) {
            currentTab = 0;
            mVm.setCurrentTab(0);
        }
        mVb.pager.setCurrentItem(currentTab);

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mVb.pager.setOffscreenPageLimit(mTabAdapter.getItemCount());
    }

    @Override
    public void onPause() {
        super.onPause();
        mVm.setCurrentTab(mVb.pager.getCurrentItem());
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
        final Context context = requireContext();

        final Book book = mVm.getBook();
        // list of fragment tags
        final Collection<String> unfinishedEdits = mVm.getUnfinishedEdits();

        final List<Fragment> fragments = getParentFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            final Fragment fragment = fragments.get(i);
            // Nor really needed to check for being a DataEditor,
            // but this leaves the possibility to add non-DataEditor fragments.
            if (fragment instanceof DataEditor) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) fragment;

//                Log.d(TAG, "checkUnfinishedEdits=" + checkUnfinishedEdits
//                           + "|tag=" + dataEditor.getFragmentId()
//                           + "|isResumed=" + dataEditor.isResumed()
//                           + "|unfinishedEdits= " + unfinishedEdits
//                                   .contains(dataEditor.getFragmentId()));

                // 1. Fragments which went through onPause (i.e. are NOT resumed)
                // have saved their *confirmed* data, but might have unfinished edits
                // as previously logged in mBookViewModel.getUnfinishedEdits()
                if (!dataEditor.isResumed()
                    && checkUnfinishedEdits
                    && unfinishedEdits.contains(dataEditor.getFragmentId())) {
                    // bring it to the front; i.e. resume it; the user will see it below the dialog.
                    mVb.pager.setCurrentItem(i);
                    StandardDialogs.unsavedEdits(context,
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
                        StandardDialogs.unsavedEdits(context,
                                                     () -> prepareSave(false),
                                                     this::setResultsAndFinish);
                        return;
                    }
                }
            }
        }

        // Now validate the book data
        if (!book.validate(context)) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.vldt_failure)
                    .setMessage(book.getValidationExceptionMessage(context))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        // Check if the book already exists
        if (mVm.bookExists()) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
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
    private void saveBook() {
        try {
            //noinspection ConstantConditions
            mVm.saveBook(getContext());
            setResultsAndFinish();

        } catch (@NonNull final CoverStorageException | DaoWriteException e) {
            Logger.error(TAG, e);
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    /** Single point of exit for this Activity. */
    public void setResultsAndFinish() {
        //noinspection ConstantConditions
        getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
        getActivity().finish();
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        /** Visible tabs as per user preferences. */
        private final List<TabInfo> mTabs = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param container hosting activity
         */
        TabAdapter(@NonNull final FragmentActivity container) {
            super(container);

            final SharedPreferences global =
                    PreferenceManager.getDefaultSharedPreferences(container);

            // Build the tab class/title list.
            mTabs.add(new TabInfo(EditBookFieldsFragment.class,
                                  R.string.tab_lbl_details,
                                  R.string.tab_lbl_details));
            mTabs.add(new TabInfo(EditBookPublicationFragment.class,
                                  R.string.tab_lbl_publication,
                                  R.string.lbl_publication));
            mTabs.add(new TabInfo(EditBookNotesFragment.class,
                                  R.string.tab_lbl_notes,
                                  R.string.lbl_personal_notes));

            if (DBKey.isUsed(global, DBKey.BITMASK_TOC)) {
                mTabs.add(new TabInfo(EditBookTocFragment.class,
                                      R.string.tab_lbl_content,
                                      R.string.lbl_table_of_content));
            }
            if (EditBookExternalIdFragment.isShowTab(global)) {
                mTabs.add(new TabInfo(EditBookExternalIdFragment.class,
                                      R.string.tab_lbl_ext_id,
                                      R.string.tab_lbl_ext_id));
            }
        }

        @NonNull
        TabInfo getTabInfo(final int position) {
            return mTabs.get(position);
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

            } catch (@NonNull final IllegalAccessException | java.lang.InstantiationException e) {
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
            @StringRes
            final int contentDescriptionId;

            TabInfo(@NonNull final Class<? extends Fragment> clazz,
                    @StringRes final int titleId,
                    @StringRes final int contentDescriptionId) {
                this.clazz = clazz;
                this.titleId = titleId;
                this.contentDescriptionId = contentDescriptionId;
            }
        }
    }
}

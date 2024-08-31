/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookBinding;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;

public class EditBookFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookActivity";
    /** Host for the tabbed fragments. */
    private TabAdapter tabAdapter;
    /** View model. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private FragmentEditBookBinding vb;
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Warn the user if the book was changed
                    if (vm.getBook().getStage() == EntityStage.Stage.Dirty) {
                        //noinspection DataFlowIssue
                        StandardDialogs.unsavedEdits(getContext(),
                                                     () -> prepareSave(true),
                                                     () -> setResultsAndFinish());
                        return;
                    }

                    setResultsAndFinish();
                }
            };
    private final ViewPager2.OnPageChangeCallback pageChange =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(final int position) {
                    hideKeyboard(vb.pager);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditBookBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.fragmentRootView(view);

        //noinspection DataFlowIssue
        final TabLayout tabPanel = getActivity().findViewById(R.id.tab_panel);

        getToolbar().addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        tabAdapter = new TabAdapter(getActivity());
        vb.pager.setAdapter(tabAdapter);

        vb.pager.registerOnPageChangeCallback(pageChange);

        new TabLayoutMediator(tabPanel, vb.pager, (tab, position) -> {
            final TabAdapter.TabInfo tabInfo = tabAdapter.getTabInfo(position);
            tab.setText(tabInfo.getTitleId());
            tab.setContentDescription(tabInfo.getContentDescriptionId());
        }).attach();
    }

    @Override
    public void onResume() {
        super.onResume();

        int currentTab = vm.getCurrentTab();
        // rotating a device can change the number of tabs shown (i.e. some tabs get combined)
        if (currentTab >= tabAdapter.getItemCount()) {
            currentTab = 0;
            vm.setCurrentTab(0);
        }
        vb.pager.setCurrentItem(currentTab);

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        vb.pager.setOffscreenPageLimit(tabAdapter.getItemCount());
    }

    @Override
    public void onPause() {
        super.onPause();
        vm.setCurrentTab(vb.pager.getCurrentItem());
    }

    @Override
    public void onDestroyView() {
        vb.pager.unregisterOnPageChangeCallback(pageChange);
        super.onDestroyView();
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
    private void prepareSave(final boolean checkUnfinishedEdits) {
        final Context context = requireContext();

        final Book book = vm.getBook();
        // list of fragment tags
        final Collection<FragmentId> unfinishedEdits = vm.getUnfinishedEdits();

        final List<Fragment> fragments = getParentFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            final Fragment fragment = fragments.get(i);
            // Not really needed to check for being a DataEditor,
            // but this leaves the possibility to add non-DataEditor fragments.
            if (fragment instanceof DataEditor) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) fragment;

                // 1. Fragments which went through onPause (i.e. are NOT resumed)
                // have saved their *confirmed* data, but might have unfinished edits
                // as previously logged in {@link EditBookViewModel#getUnfinishedEdits()}
                if (!dataEditor.isResumed()
                    && checkUnfinishedEdits
                    && unfinishedEdits.contains(dataEditor.getFragmentId())) {
                    // bring it to the front; i.e. resume it; the user will see it below the dialog.
                    vb.pager.setCurrentItem(i);
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
                        vb.pager.setCurrentItem(i);
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
                    .setIcon(R.drawable.error_24px)
                    .setTitle(R.string.vldt_failure)
                    .setMessage(book.getValidationExceptionMessage(context))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        // Check if the book already exists
        if (vm.bookExists()) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.warning_24px)
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
            //noinspection DataFlowIssue
            vm.saveBook(getContext());
            setResultsAndFinish();

        } catch (@NonNull final StorageException e) {
            ErrorDialog.show(getContext(), TAG, e);

        } catch (@NonNull final DaoWriteException e) {
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    /** Single point of exit for this Activity. */
    private void setResultsAndFinish() {
        //noinspection DataFlowIssue
        getActivity().setResult(Activity.RESULT_OK, vm.createResultIntent());
        getActivity().finish();
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        /** Visible tabs as per user preferences. */
        private final List<TabInfo> tabList = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param container hosting activity
         */
        TabAdapter(@NonNull final FragmentActivity container) {
            super(container);

            // Build the tab class/title list.
            tabList.add(new TabInfo(EditBookFieldsFragment.class,
                                    R.string.lbl_tab_details,
                                    R.string.lbl_tab_details));
            tabList.add(new TabInfo(EditBookPublicationFragment.class,
                                    R.string.lbl_tab_publication,
                                    R.string.lbl_publication));

            // On tablets the notes fields are incorporated in the publication fragment
            // On small screens (i.e. phones) they get their own tab
            if (!container.getResources().getBoolean(R.bool.combine_book_edit_tabs)) {
                tabList.add(new TabInfo(EditBookNotesFragment.class,
                                        R.string.lbl_tab_notes,
                                        R.string.lbl_personal_notes));
            }

            if (ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_TOC_ENTRY)) {
                tabList.add(new TabInfo(EditBookTocFragment.class,
                                        R.string.lbl_tab_table_of_content,
                                        R.string.lbl_table_of_content));
            }
            if (EditBookExternalIdFragment.isShowTab(container)) {
                tabList.add(new TabInfo(EditBookExternalIdFragment.class,
                                        R.string.lbl_tab_lbl_ext_id,
                                        R.string.lbl_tab_lbl_ext_id));
            }
        }

        @NonNull
        TabInfo getTabInfo(final int position) {
            return tabList.get(position);
        }

        @Override
        public int getItemCount() {
            return tabList.size();
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return tabList.get(position).createFragment();
        }

        /** Value class to match up a tab fragment class and the title to use for the tab. */
        private static class TabInfo {

            @NonNull
            private final Class<? extends Fragment> clazz;
            @StringRes
            private final int titleId;
            @StringRes
            private final int contentDescriptionId;

            TabInfo(@NonNull final Class<? extends Fragment> clazz,
                    @StringRes final int titleId,
                    @StringRes final int contentDescriptionId) {
                this.clazz = clazz;
                this.titleId = titleId;
                this.contentDescriptionId = contentDescriptionId;
            }

            @NonNull
            Fragment createFragment() {
                try {
                    return clazz.getConstructor().newInstance();

                } catch (@NonNull final IllegalAccessException | java.lang.InstantiationException
                                        | NoSuchMethodException | InvocationTargetException e) {
                    // We'll never get here...
                    throw new IllegalStateException(e);
                }
            }

            @StringRes
            int getTitleId() {
                return titleId;
            }

            @StringRes
            int getContentDescriptionId() {
                return contentDescriptionId;
            }
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.toolbar_action_save, menu);

            final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_SAVE);
            //noinspection DataFlowIssue
            final Button button = menuItem.getActionView().findViewById(R.id.btn_save);
            button.setOnClickListener(v -> onMenuItemSelected(menuItem));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_ACTION_SAVE) {
                prepareSave(true);
                return true;
            }
            return false;
        }
    }

}

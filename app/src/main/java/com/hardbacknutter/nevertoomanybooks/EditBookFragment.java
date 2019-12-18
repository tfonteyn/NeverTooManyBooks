/*
 * @Copyright 2019 HardBackNutter
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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
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

    /**
     * Tabs in order.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String REQUEST_BKEY_TAB = "tab";
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT = 0;
    @SuppressWarnings("unused")
    public static final int TAB_EDIT_PUBLICATION = 1;
    @SuppressWarnings("unused")
    public static final int TAB_EDIT_NOTES = 2;
    @SuppressWarnings("unused")
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    private FragmentActivity mHostActivity;

    private ViewPager2 mViewPager;
    private TabAdapter mTabAdapter;

    /** The book. Must be in the Activity scope. */
    private BookBaseFragmentModel mBookModel;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mHostActivity = (FragmentActivity) context;
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
        int showTab;
        if (currentArgs != null) {
            showTab = currentArgs.getInt(REQUEST_BKEY_TAB, TAB_EDIT);
        } else {
            showTab = TAB_EDIT;
        }

        mTabAdapter = new TabAdapter(this);

        mViewPager.setAdapter(mTabAdapter);

        // The FAB lives in the activity.
        FloatingActionButton fabButton = mHostActivity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_save);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> doSave());

        // The tab bar lives in the activity layout inside the AppBarLayout!
        TabLayout tabLayout = mHostActivity.findViewById(R.id.tab_panel);

        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) ->
                tab.setText(getString(mTabAdapter.getTabTitle(position))))
                .attach();

        // sanity check
        if (showTab > mTabAdapter.getItemCount()) {
            throw new UnexpectedValueException(showTab);
        }
        mViewPager.setCurrentItem(showTab);
    }

    /**
     * Add the menu items which are common to all child fragments.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        Resources r = getResources();
        // onOptionsItemSelected: MENU_BOOK_UPDATE_FROM_INTERNET handled in super.
        menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                 r.getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                 R.string.menu_update_fields)
            .setIcon(R.drawable.ic_cloud_download);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Set visibility of menu items as appropriate.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.MENU_UPDATE_FROM_INTERNET).setVisible(mBookModel.isExistingBook());
    }

    /**
     * Called when the user clicks 'save'.
     * <p>
     * Save a book into the database, by either updating or created a book.
     * Validation is done before saving.
     * Checks if the book already exists (isbn search) when the user is creating a book;
     * if so we prompt to confirm.
     */
    private void doSave() {

        // Ask any fragment that has not gone into 'onPause' to add its fields.
        // There should only be the one fragment, i.e. the front/current fragment.
        // Paranoia...
        for (Fragment frag : getChildFragmentManager().getFragments()) {
            if (frag.isResumed()) {
                //Logger.debug(this,"doSave", frag);
                ((DataEditor) frag).saveFields();
            }
        }

        Book book = mBookModel.getBook();
        // Now validate the book data
        //noinspection ConstantConditions
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

        if (book.getId() == 0) {
            String isbn = book.getString(DBDefinitions.KEY_ISBN);
            // Check if the book already exists
            if (!isbn.isEmpty() && ((mBookModel.getDb().getBookIdFromIsbn(isbn) > 0))) {
                new AlertDialog.Builder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.title_duplicate_book)
                        .setMessage(R.string.confirm_duplicate_book_message)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        // User aborts this edit
                        .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                                mHostActivity.finish())
                        // User wants to continue editing this book
                        .setNeutralButton(R.string.edit, (dialog, which) -> dialog.dismiss())
                        // User wants to add regardless
                        .setPositiveButton(R.string.btn_confirm_add, (dialog, which) -> saveBook())
                        .create()
                        .show();
                return;
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
        mBookModel.saveBook(getContext());

        Intent resultData = mBookModel.getActivityResultData();
        mHostActivity.setResult(Activity.RESULT_OK, resultData);
        mHostActivity.finish();
    }

    /**
     * the only thing on this level is the TAB we're on.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(REQUEST_BKEY_TAB, mViewPager.getCurrentItem());
    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        TabAdapter(@NonNull final Fragment container) {
            super(container);
        }

        @Override
        public int getItemCount() {
            return 4;
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            switch (position) {
                case 0:
                    return new EditBookFieldsFragment();

                case 1:
                    return new EditBookPublicationFragment();

                case 2:
                    return new EditBookNotesFragment();

                case 3:
                    return new EditBookTocFragment();

                default:
                    throw new UnexpectedValueException(position);
            }
        }

        @StringRes
        int getTabTitle(final int position) {
            switch (position) {
                case 0:
                    return R.string.tab_lbl_details;

                case 1:
                    return R.string.tab_lbl_publication;

                case 2:
                    return R.string.tab_lbl_notes;

                case 3:
                    return R.string.tab_lbl_content;

                default:
                    throw new UnexpectedValueException(position);
            }
        }
    }
}

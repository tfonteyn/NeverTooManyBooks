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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Fragment that hosts child fragments to edit a book.
 * <p>
 * Note that this class does not (need to) extend {@link EditBookBaseFragment}
 * but merely {@link BookBaseFragment}.
 */
public class EditBookFragment
        extends BookBaseFragment {

    /** Fragment manager tag. */
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

    private AppCompatActivity mActivity;

    private ViewPager mViewPager;
    private ViewPagerAdapter mPagerAdapter;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
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

        // any specific tab desired as 'selected' ?
        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : getArguments();
        int showTab;
        if (currentArgs != null) {
            showTab = currentArgs.getInt(REQUEST_BKEY_TAB, TAB_EDIT);
        } else {
            showTab = TAB_EDIT;
        }

        FragmentManager fm = getChildFragmentManager();
        mPagerAdapter = new ViewPagerAdapter(fm);
        // add them in order! i.e. in the order the TAB_* constants are defined.
        mPagerAdapter.add(new FragmentHolder(fm, EditBookFieldsFragment.TAG,
                                             getString(R.string.tab_lbl_details)));
        mPagerAdapter.add(new FragmentHolder(fm, EditBookPublicationFragment.TAG,
                                             getString(R.string.tab_lbl_publication)));
        mPagerAdapter.add(new FragmentHolder(fm, EditBookNotesFragment.TAG,
                                             getString(R.string.tab_lbl_notes)));
        mPagerAdapter.add(new FragmentHolder(fm, EditBookTocFragment.TAG,
                                             getString(R.string.tab_lbl_content)));

        mViewPager.setAdapter(mPagerAdapter);

        // The FAB lives in the activity.
        FloatingActionButton fabButton = mActivity.findViewById(R.id.fab);
        fabButton.setOnClickListener(v -> doSave());

        // The tab bar lives in the activity layout inside the AppBarLayout!
        TabLayout tabLayout = mActivity.findViewById(R.id.tab_panel);

        tabLayout.setupWithViewPager(mViewPager);
        // sanity check
        if (showTab > mPagerAdapter.getCount()) {
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

        //noinspection ConstantConditions
        LocaleUtils.insanityCheck(getContext());
        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD, MenuHandler.ORDER_HIDE_KEYBOARD,
                 R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // sing FAB now
//        int saveOrAddText = mBookModel.isExistingBook() ? R.string.btn_confirm_save
//                                                        : R.string.btn_confirm_add;
//        menu.add(Menu.NONE, R.id.MENU_SAVE, MenuHandler.ORDER_SAVE, saveOrAddText)
//            .setIcon(R.drawable.ic_save)
//            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET, MenuHandler.ORDER_UPDATE_FIELDS,
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

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {
            case R.id.MENU_SAVE:
                doSave();
                return true;

            case R.id.MENU_HIDE_KEYBOARD:
                //noinspection ConstantConditions
                App.hideKeyboard(getView());
                return true;

            default:
                // MENU_BOOK_UPDATE_FROM_INTERNET handled in super.
                return super.onOptionsItemSelected(item);
        }
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

        // ask any fragment that has not gone into 'onPause' to add its fields.
        // We could just ask the 'current' fragment which would be enough.
        // Call it an experiment...
        for (int p = 0; p < mPagerAdapter.getCount(); p++) {
            Fragment frag = mPagerAdapter.getItem(p);
            if (frag.isResumed()) {
                ((DataEditor) frag).saveFields();
            }
        }

        Book book = mBookModel.getBook();
        // Now validate the book data
        if (!book.validate()) {
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.vldt_failure)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(book.getValidationExceptionMessage(getContext()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        if (book.getId() == 0) {
            String isbn = book.getString(DBDefinitions.KEY_ISBN);
            /* Check if the book already exists */
            if (!isbn.isEmpty() && ((mBookModel.getDb().getBookIdFromIsbn(isbn, true) > 0))) {
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.title_duplicate_book)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.confirm_duplicate_book_message)
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel,
                                           (d, which) -> mActivity.finish())
                        .setPositiveButton(android.R.string.ok, (d, which) -> saveBook())
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
        Book book = mBookModel.saveBook(getContext());
        Intent data = new Intent().putExtra(DBDefinitions.KEY_PK_ID, book.getId());
        mActivity.setResult(Activity.RESULT_OK, data);
        mActivity.finish();
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

    private static class ViewPagerAdapter
            extends FragmentPagerAdapter {

        private final List<FragmentHolder> mFragmentList = new ArrayList<>(5);

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        ViewPagerAdapter(@NonNull final FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        @NonNull
        public Fragment getItem(final int position) {
            return mFragmentList.get(position).getFragment();
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).getPageTitle();
        }

        void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }
    }

    private static class FragmentHolder {

        @NonNull
        private final String mTitle;
        @NonNull
        private Fragment mFragment;

        /**
         * Constructor.
         *
         * @param fm    FragmentManager
         * @param tag   of the fragment to create
         * @param title the title/label to put on the tab
         */
        FragmentHolder(@NonNull final FragmentManager fm,
                       @NonNull final String tag,
                       @NonNull final String title) {
            mTitle = title;

            //noinspection ConstantConditions
            mFragment = fm.findFragmentByTag(tag);
            if (mFragment == null) {
                switch (tag) {
                    case EditBookFieldsFragment.TAG:
                        mFragment = new EditBookFieldsFragment();
                        break;

                    case EditBookPublicationFragment.TAG:
                        mFragment = new EditBookPublicationFragment();
                        break;

                    case EditBookNotesFragment.TAG:
                        mFragment = new EditBookNotesFragment();
                        break;

                    case EditBookTocFragment.TAG:
                        mFragment = new EditBookTocFragment();
                        break;

                    default:
                        throw new UnexpectedValueException(tag);
                }
            }
        }

        @NonNull
        String getPageTitle() {
            return mTitle;
        }

        @NonNull
        Fragment getFragment() {
            return mFragment;
        }
    }
}

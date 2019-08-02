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

package com.hardbacknutter.nevertomanybooks;

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

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.utils.Utils;
import com.google.android.material.tabs.TabLayout;

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
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_PUBLICATION = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_NOTES = 2;
    @SuppressWarnings("WeakerAccess")
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
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;
        int tabWanted;
        if (args != null) {
            tabWanted = args.getInt(REQUEST_BKEY_TAB, TAB_EDIT);
        } else {
            tabWanted = TAB_EDIT;
        }

        int showTab = TAB_EDIT;
        switch (tabWanted) {
            case TAB_EDIT:
            case TAB_EDIT_PUBLICATION:
            case TAB_EDIT_NOTES:
                showTab = tabWanted;
                break;

            case TAB_EDIT_ANTHOLOGY:
                if (App.isUsed(DBDefinitions.KEY_TOC_BITMASK)) {
                    showTab = tabWanted;
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown tab=" + tabWanted);
        }

        FragmentManager fm = getChildFragmentManager();
        mPagerAdapter = new ViewPagerAdapter(fm);
        // add them in order! i.e. in the order the TAB_* constants are defined.
        mPagerAdapter.add(new FragmentHolder(fm, EditBookFieldsFragment.TAG,
                                             getString(R.string.tab_lbl_details)));
        mPagerAdapter.add(new FragmentHolder(fm, EditBookPublicationFragment.TAG,
                                             getString(R.string.lbl_publication)));
        mPagerAdapter.add(new FragmentHolder(fm, EditBookNotesFragment.TAG,
                                             getString(R.string.tab_lbl_notes)));
        if (App.isUsed(DBDefinitions.KEY_TOC_BITMASK)) {
            mPagerAdapter.add(new FragmentHolder(fm, EditBookTocFragment.TAG,
                                                 getString(R.string.tab_lbl_content)));
        }

        mViewPager.setAdapter(mPagerAdapter);

        // note that the tab bar lives in the activity layout inside the AppBarLayout!
        TabLayout tabLayout = mActivity.findViewById(R.id.tab_panel);

        tabLayout.setupWithViewPager(mViewPager);
        mViewPager.setCurrentItem(showTab);
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

    /**
     * Add the menu items which are common to all child fragments.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        int saveOrAddText = mBookBaseFragmentModel.isExistingBook() ? R.string.btn_confirm_save
                                                                    : R.string.btn_confirm_add;

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD,
                 MenuHandler.ORDER_HIDE_KEYBOARD, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_SAVE,
                 MenuHandler.ORDER_SAVE, saveOrAddText)
            .setIcon(R.drawable.ic_save)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(R.id.MENU_UPDATE_FROM_INTERNET, R.id.MENU_UPDATE_FROM_INTERNET,
                 MenuHandler.ORDER_UPDATE_FIELDS, R.string.lbl_update_fields)
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

        menu.setGroupVisible(R.id.MENU_UPDATE_FROM_INTERNET,
                             mBookBaseFragmentModel.isExistingBook());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {
            case R.id.MENU_SAVE:
                doSave();
                return true;

            case R.id.MENU_HIDE_KEYBOARD:
                //noinspection ConstantConditions
                Utils.hideKeyboard(getView());
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
     * <p>
     * It will check if the book already exists (isbn search) if you are creating a book;
     * if so the user will be prompted to confirm.
     *
     * <br>Validation is done in two steps:
     * <ol>
     * <li>The data in the fields, done on a per-fragment base in
     * {@link EditBookBaseFragment#onSaveFieldsToBook()}<br>
     * Limited to one cross-validation between start/end dates for reading</li>
     * <li>the book data, done here.<br>
     * These are *data* checks, e.g. date formats, boolean, ...</li>
     * </ol>
     * FIXME: validation is awkward and incomplete.
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

        // Reminder: Field validation is done on a per-fragment basis when Field values
        // are transferred to DataManager (Book) values.

        Book book = mBookBaseFragmentModel.getBook();
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

//        // Ignore validation failures; but we still validate to get the current values updated.
//        book.validate();
//
//        // However, there is some data that we really do require...
//        if (!book.containsKey(DBDefinitions.KEY_TITLE)
//                || book.getString(DBDefinitions.KEY_TITLE).isEmpty()) {
//            UserMessage.show(getView(), R.string.warning_required_title);
//            return;
//        }
//
//        if (book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty()) {
//            UserMessage.show(getView(), R.string.warning_required_author_long);
//            return;
//        }

        if (book.getId() == 0) {
            String isbn = book.getString(DBDefinitions.KEY_ISBN);
            /* Check if the book already exists */
            if (!isbn.isEmpty() && ((mBookBaseFragmentModel.getDb().getBookIdFromIsbn(isbn,
                                                                                      true) > 0))) {
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.title_duplicate_book)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.confirm_duplicate_book_message)
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (d, which) ->
                                mActivity.finish())
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
        Book book = mBookBaseFragmentModel.saveBook();

        Intent data = new Intent().putExtra(DBDefinitions.KEY_PK_ID, book.getId());
        mActivity.setResult(Activity.RESULT_OK, data);
        mActivity.finish();
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

        void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).getTitle();
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
                        throw new IllegalArgumentException("tag=" + tag);
                }
            }
        }

        @NonNull
        String getTitle() {
            return mTitle;
        }

        @NonNull
        Fragment getFragment() {
            return mFragment;
        }
    }
}

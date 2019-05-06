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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.dialogs.AlertDialogListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.google.android.material.tabs.TabLayout;

/**
 * Fragment that hosts child fragments to edit a book.
 * 1. Details
 * 2. Notes
 * 3. Anthology titles
 */
public class EditBookFragment
        extends EditBookBaseFragment
        implements DataEditor {

    /** Fragment manager tag. */
    public static final String TAG = EditBookFragment.class.getSimpleName();

    /**
     * Tabs in order.
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    public static final int TAB_EDIT = 0;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_PUBLICATION = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_NOTES = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int TAB_EDIT_ANTHOLOGY = 3;

    private BaseActivity mActivity;

    private ViewPager mViewPager;
    private ViewPagerAdapter mPagerAdapter;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mActivity = (BaseActivity) getActivity();
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

        //noinspection ConstantConditions
        mViewPager = getView().findViewById(R.id.tab_fragment);
        mViewPager.setAdapter(mPagerAdapter);

        // note that the tab bar lives in the activity layout in the AppBarLayout!
        //noinspection ConstantConditions
        TabLayout tabLayout = getActivity().findViewById(R.id.tab_panel);

        tabLayout.setupWithViewPager(mViewPager);
        mViewPager.setCurrentItem(showTab);

        boolean isExistingBook = mBookBaseFragmentModel.getBook().getId() > 0;
        Button confirmButton = getView().findViewById(R.id.confirm);
        confirmButton.setText(isExistingBook ? R.string.btn_confirm_save_book
                                             : R.string.btn_confirm_add_book);

        confirmButton.setOnClickListener(v -> doSave());

        getView().findViewById(R.id.cancel).setOnClickListener(v -> doCancel());
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

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // do nothing here. Child fragments will add their own menus
    }

    /**
     * Called when the user clicks 'save'.
     * <p>
     * Save a book into the database, by either updating or created a book.
     * <p>
     * It will check if the book already exists (isbn search) if you are creating a book;
     * if so the user will be prompted to confirm.
     * <p>
     * In all cases, once the book is added/created, or not, the appropriate method of the
     * passed nextStep parameter will be executed. Passing nextStep is necessary because
     * this method may return after displaying a dialogue.
     */
    private void doSave() {

        // ask any page that has not gone into 'onPause' to add its fields.
        for (int p = 0; p < mPagerAdapter.getCount(); p++) {
            Fragment frag = mPagerAdapter.getItem(p);
            if (frag.isResumed()) {
                ((DataEditor) frag).saveFields();
            }
        }

        // Ignore validation failures; but we still validate to get the current values updated.
        mBookBaseFragmentModel.getBook().validate();
        // if (!book.validate()) {
        //      StandardDialogs.sendTaskUserMessage(this,
        //      book.getValidationExceptionMessage(getResources()));
        // }
        // However, there is some data that we really do require...
        if (mBookBaseFragmentModel.getBook().getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty()) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.warning_required_author_long);
            return;
        }
        if (!mBookBaseFragmentModel.getBook().containsKey(
                DBDefinitions.KEY_TITLE) || mBookBaseFragmentModel.getBook().getString(
                DBDefinitions.KEY_TITLE).isEmpty()) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.warning_required_title);
            return;
        }

        AlertDialogListener nextStep = new AlertDialogListener() {
            @Override
            public void onPositiveButton() {
                saveBook();
                Intent data = new Intent().putExtra(DBDefinitions.KEY_ID,
                                                    mBookBaseFragmentModel.getBook().getId());
                mActivity.setResult(Activity.RESULT_OK, data);
                mActivity.finish();
            }

            @Override
            public void onNegativeButton() {
                doCancel();
            }
        };

        if (mBookBaseFragmentModel.getBook().getId() == 0) {
            String isbn = mBookBaseFragmentModel.getBook().getString(DBDefinitions.KEY_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty() && ((mDb.getBookIdFromIsbn(isbn, true) > 0))) {
                //noinspection ConstantConditions
                StandardDialogs.confirmSaveDuplicateBook(getContext(), nextStep);
                return;
            }
        }

        // No special actions required...just do it.
        nextStep.onPositiveButton();
    }

    /**
     * Note that when the user clicks 'cancel' we still put all the fields into the book.
     * So if they subsequently cancel the cancel in {@link BaseActivity#finishIfClean(boolean)}
     * we can resume with the latest data.
     */
    private void doCancel() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        Intent data = new Intent().putExtra(DBDefinitions.KEY_ID, mBookBaseFragmentModel.getBook().getId());
        //ENHANCE: global changes not detected, so assume they happened.
        mActivity.setResult(Activity.RESULT_OK, data);
        mActivity.finishIfClean(mBookBaseFragmentModel.isDirty());
    }

    /**
     * Save the collected book details.
     */
    private void saveBook() {
        if (mBookBaseFragmentModel.getBook().getId() == 0) {
            long id = mDb.insertBook(mBookBaseFragmentModel.getBook());
            if (id > 0) {
                // if we got a cover while searching the internet, make it permanent
                if (mBookBaseFragmentModel.getBook().getBoolean(UniqueId.BKEY_COVER_IMAGE)) {
                    String uuid = mDb.getBookUuid(id);
                    // get the temporary downloaded file
                    File source = StorageUtils.getTempCoverFile();
                    File destination = StorageUtils.getCoverFile(uuid);
                    // and rename it to the permanent UUID one.
                    StorageUtils.renameFile(source, destination);
                }
            }
        } else {
            mDb.updateBook(mBookBaseFragmentModel.getBook().getId(), mBookBaseFragmentModel.getBook(), 0);
        }
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
            super(fm, RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        @NonNull
        public Fragment getItem(final int position) {
            return mFragmentList.get(position).fragment;
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).title;
        }
    }

    private static class FragmentHolder {

        @NonNull
        final String title;
        @NonNull
        Fragment fragment;

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        FragmentHolder(@NonNull final FragmentManager fm,
                       @NonNull final String tag,
                       @NonNull final String title) {
            this.title = title;

            //noinspection ConstantConditions
            fragment = fm.findFragmentByTag(tag);
            if (fragment == null) {
                if (EditBookFieldsFragment.TAG.equals(tag)) {
                    fragment = new EditBookFieldsFragment();

                } else if (EditBookPublicationFragment.TAG.equals(tag)) {
                    fragment = new EditBookPublicationFragment();

                } else if (EditBookNotesFragment.TAG.equals(tag)) {
                    fragment = new EditBookNotesFragment();

                } else if (EditBookTocFragment.TAG.equals(tag)) {
                    fragment = new EditBookTocFragment();

                }
            }
        }
    }
}

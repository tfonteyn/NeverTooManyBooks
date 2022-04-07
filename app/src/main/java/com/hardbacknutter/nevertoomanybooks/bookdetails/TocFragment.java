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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.TocBaseAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowTocEntryBinding;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public class TocFragment
        extends BaseFragment {

    public static final String TAG = "TocFragment";

    /** View Binding. */
    private FragmentTocBinding mVb;

    private TocViewModel mVm;

    /** The Adapter. */
    private TocAdapter mAdapter;

    /**
     * In embedded mode, we just need/display the list itself.
     *
     * @param toc list
     *
     * @return new instance
     */
    @NonNull
    public static Fragment createEmbedded(@NonNull final ArrayList<TocEntry> toc) {
        final Fragment fragment = new TocFragment();
        final Bundle args = new Bundle(1);
        args.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * In full screen mode, we display the authors and book title as the Toolbar title/subtitle
     *
     * @param toc  list
     * @param book for Toolbar info display
     *
     * @return new instance
     */
    @NonNull
    public static Fragment create(@NonNull final ArrayList<TocEntry> toc,
                                  @NonNull final Book book) {
        final Fragment fragment = new TocFragment();
        final Bundle args = new Bundle(4);
        args.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
        args.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, book.getAuthors());
        args.putString(DBKey.KEY_TITLE, book.getTitle());
        args.putLong(DBKey.FK_BOOK, book.getId());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(TocViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentTocBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @CallSuper
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        final Context context = getContext();

        //noinspection ConstantConditions
        mVb.toc.addItemDecoration(new DividerItemDecoration(context, RecyclerView.VERTICAL));

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final int overlayType = Prefs.getFastScrollerOverlayType(global);
        FastScroller.attach(mVb.toc, overlayType);

        mAdapter = new TocAdapter(context, mVm.getList());
        mVb.toc.setAdapter(mAdapter);
        mVb.toc.setHasFixedSize(true);

        // Author/Book-title are only present when this fragment is full-screen
        final String authors = mVm.getAuthors();
        if (authors != null) {
            toolbar.setTitle(authors);
        }
        final String bookTitle = mVm.getBookTitle();
        if (bookTitle != null) {
            toolbar.setSubtitle(bookTitle);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void reload(@NonNull final List<TocEntry> tocList) {
        mVm.reload(tocList);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Row ViewHolder for {@link AuthorWorksFragment.TocAdapter}.
     */
    private static class Holder
            extends TocBaseAdapter.TocHolder {

        @NonNull
        private final RowTocEntryBinding vb;

        Holder(@NonNull final RowTocEntryBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @NonNull
        @Override
        public ImageButton getIconBtnView() {
            return vb.btnType;
        }

        @NonNull
        @Override
        public TextView getTitleView() {
            return vb.title;
        }

        @NonNull
        @Override
        public TextView getFirstPublicationView() {
            return vb.year;
        }

        @NonNull
        public TextView getAuthorView() {
            return vb.author;
        }
    }

    private static class TocAdapter
            extends TocBaseAdapter {

        /**
         * Constructor.
         *
         * @param context Current context.
         * @param tocList to show
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        TocAdapter(@NonNull final Context context,
                   @NonNull final List<AuthorWork> tocList) {
            super(context, tocList);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowTocEntryBinding vb = RowTocEntryBinding.inflate(mInflater, parent, false);
            final Holder holder = new Holder(vb);
            initTypeButton(holder, viewType);

            return holder;
        }
    }
}

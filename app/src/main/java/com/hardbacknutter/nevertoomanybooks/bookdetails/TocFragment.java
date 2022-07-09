/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.TocBaseAdapter;
import com.hardbacknutter.nevertoomanybooks.TocEntryHandler;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;

public class TocFragment
        extends BaseFragment {

    public static final String TAG = "TocFragment";
    static final String BKEY_EMBEDDED = TAG + ":emb";

    /** View Binding. */
    private FragmentTocBinding vb;

    private TocViewModel vm;
    private ShowBookDetailsActivityViewModel aVm;

    /** Callback - used when we're running inside another component; e.g. the BoB. */
    @Nullable
    private BookChangedListener bookChangedListener;

    /** Display a Book. From there the user could edit it... so we must propagate the result. */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), o -> o.ifPresent(
                    data -> {
                        aVm.updateFragmentResult(data);
                        if (bookChangedListener != null && data.modified) {
                            bookChangedListener.onBookUpdated(vm.getBookId(), (String) null);
                        }
                    }));

    /** The Adapter. */
    private TocAdapter adapter;

    @NonNull
    public static Fragment create(@NonNull final Book book,
                                  final boolean embedded,
                                  @NonNull final Style style) {
        final Fragment fragment = new TocFragment();
        final Bundle args = new Bundle(6);
        args.putBoolean(BKEY_EMBEDDED, embedded);
        args.putString(DBKey.STYLE_UUID, style.getUuid());
        args.putLong(DBKey.FK_BOOK, book.getId());
        args.putString(DBKey.TITLE, book.getTitle());
        args.putParcelableArrayList(Book.BKEY_TOC_LIST, new ArrayList<>(book.getToc()));
        args.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, new ArrayList<>(book.getAuthors()));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (context instanceof BookChangedListener) {
            bookChangedListener = (BookChangedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();

        //noinspection ConstantConditions
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        //noinspection ConstantConditions
        aVm.init(getContext(), args);

        vm = new ViewModelProvider(this).get(TocViewModel.class);
        vm.init(getContext(), args);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentTocBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @CallSuper
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        //noinspection ConstantConditions
        vb.toc.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));

        final int overlayType = Prefs.getFastScrollerOverlayType(context);
        FastScroller.attach(vb.toc, overlayType);

        adapter = new TocAdapter(context, vm.getPrimaryAuthor(), vm.getWorks(), this::gotoBook);
        vb.toc.setAdapter(adapter);
        vb.toc.setHasFixedSize(true);

        if (!vm.isEmbedded()) {
            final Toolbar toolbar = getToolbar();
            vm.getScreenTitle().ifPresent(toolbar::setTitle);
            vm.getScreenSubtitle().ifPresent(toolbar::setSubtitle);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void reload(@NonNull final Book book) {
        //noinspection ConstantConditions
        vm.reload(getContext(), book);
        adapter.notifyDataSetChanged();
    }

    private void gotoBook(final int position) {
        final AuthorWork work = vm.getWorks().get(position);

        switch (work.getWorkType()) {
            case TocEntry: {
                final TocEntry tocEntry = (TocEntry) work;
                final ArrayList<Long> bookIdList = vm.getBookIds(tocEntry);
                if (bookIdList.size() == 1) {
                    displayBookLauncher.launch(new ShowBookPagerContract.Input(
                            bookIdList.get(0), aVm.getStyle().getUuid(), null, 0));

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    final Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                      (Parcelable) RebuildBooklist.Expanded);

                    //TODO: see AuthorWorksFragment
//                    if (vm.isAllBookshelves()) {
//                        intent.putExtra(BooksOnBookshelfViewModel.BKEY_BOOKSHELF,
//                                        Bookshelf.ALL_BOOKS);
//                    }
                    startActivity(intent);
                }
                break;
            }
            case Book:
            case BookLight: {
                displayBookLauncher.launch(new ShowBookPagerContract.Input(
                        work.getId(), aVm.getStyle().getUuid(), null, 0));

                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    private static class TocAdapter
            extends TocBaseAdapter {

        @NonNull
        private final TocEntryHandler tocEntryHandler;

        /**
         * Constructor.
         *
         * @param context Current context.
         * @param tocList to show
         */
        TocAdapter(@NonNull final Context context,
                   @Nullable final Author primaryAuthor,
                   @NonNull final List<AuthorWork> tocList,
                   @NonNull final TocEntryHandler tocEntryHandler) {
            super(context, primaryAuthor, tocList);
            this.tocEntryHandler = tocEntryHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final Holder holder = super.onCreateViewHolder(parent, viewType);

            //URGENT TEST
            // click -> get the book(s) for that entry and display.
//            holder.itemView.setOnClickListener(
//             v -> tocEntryHandler.viewBook(holder.getBindingAdapterPosition()));

            return holder;
        }
    }
}

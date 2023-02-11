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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.DisplayBookLauncher;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

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
    private DisplayBookLauncher displayBookLauncher;

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

        displayBookLauncher = new DisplayBookLauncher(this, o -> o.ifPresent(
                data -> {
                    aVm.setDataModified(data);
                    if (bookChangedListener != null && data.isModified()) {
                        bookChangedListener.onBookUpdated(vm.getBookId(), (String) null);
                    }
                }));

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

        adapter = new TocAdapter(context, aVm.getStyle(), vm.getPrimaryAuthor(), vm.getWorks());
        adapter.setOnRowClickListener((v, position) -> {
            // If there's only one book, there is no point doing this
            // as we're already on the book.
            final AuthorWork work = vm.getWorks().get(position);
            if (work.getBookCount() > 1) {
                // TODO: allBookshelves see AuthorWorksFragment
                displayBookLauncher.launch(this, work, aVm.getStyle(), false);
            }
        });

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
}

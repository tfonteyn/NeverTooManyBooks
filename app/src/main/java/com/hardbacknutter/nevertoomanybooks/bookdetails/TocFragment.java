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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowTocEntryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public class TocFragment
        extends BaseFragment {

    public static final String TAG = "TocFragment";

    /** View Binding. */
    private FragmentTocBinding mVb;

    private TocViewModel mVm;

    /** The Adapter. */
    @SuppressWarnings("FieldCanBeLocal")
    private TocAdapter mAdapter;

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

        final Context context = getContext();

        //noinspection ConstantConditions
        mVb.toc.addItemDecoration(new DividerItemDecoration(context, RecyclerView.VERTICAL));

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final int overlayType = Prefs.getFastScrollerOverlayType(global);
        FastScroller.attach(mVb.toc, overlayType);

        mAdapter = new TocAdapter(context, mVm.getTocList());
        mVb.toc.setAdapter(mAdapter);
        mVb.toc.setHasFixedSize(true);

        // Author/Book-title are only present when this fragment is full-screen
        final String authors = mVm.getAuthors();
        if (authors != null) {
            setTitle(authors);
        }
        final String bookTitle = mVm.getBookTitle();
        if (bookTitle != null) {
            setSubtitle(bookTitle);
        }
    }

    private static class TocAdapter
            extends RecyclerView.Adapter<TocAdapter.Holder>
            implements FastScroller.PopupTextProvider {

        /** Cached inflater. */
        @NonNull
        private final LayoutInflater mInflater;
        @NonNull
        private final List<TocEntry> mTocList;

        /**
         * Constructor.
         *
         * @param context Current context.
         * @param tocList to show
         */
        TocAdapter(@NonNull final Context context,
                   @NonNull final List<TocEntry> tocList) {
            mInflater = LayoutInflater.from(context);
            mTocList = tocList;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowTocEntryBinding vb = RowTocEntryBinding.inflate(mInflater, parent, false);
            return new Holder(vb);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final Context context = mInflater.getContext();

            final TocEntry tocEntry = mTocList.get(position);

            holder.vb.title.setText(tocEntry.getLabel(context));
            holder.vb.author.setText(tocEntry.getPrimaryAuthor().getLabel(context));

            final PartialDate date = tocEntry.getFirstPublicationDate();
            if (date.isEmpty()) {
                holder.vb.year.setVisibility(View.GONE);
            } else {
                // show full date string (if available)
                holder.vb.year.setText(context.getString(R.string.brackets, date.getIsoString()));
                holder.vb.year.setVisibility(View.VISIBLE);
            }

            // show the icon if this entry appears in more than one book in our collection
            if (tocEntry.getBookCount() > 1) {
                holder.vb.btnMultipleBooks.setVisibility(View.VISIBLE);
                holder.vb.btnMultipleBooks.setOnClickListener(v -> {
                    final String titles = tocEntry
                            .getBookTitles()
                            .stream()
                            .map(bt -> context.getString(R.string.list_element, bt.second))
                            .collect(Collectors.joining("\n"));
                    StandardDialogs.infoPopup(
                            holder.vb.btnMultipleBooks,
                            context.getString(R.string.lbl_story_in_multiple_books, titles));
                });
            }
        }

        @Override
        public int getItemCount() {
            return mTocList.size();
        }

        @NonNull
        @Override
        public String[] getPopupText(final int position) {
            final String title = mTocList.get(position).getLabel(mInflater.getContext());
            return new String[]{title};
        }

        static class Holder
                extends RecyclerView.ViewHolder {

            @NonNull
            private final RowTocEntryBinding vb;

            Holder(@NonNull final RowTocEntryBinding vb) {
                super(vb.getRoot());
                this.vb = vb;
            }
        }
    }
}

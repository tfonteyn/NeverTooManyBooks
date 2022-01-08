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
package com.hardbacknutter.nevertoomanybooks;

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
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowTocEntryWithAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public class TocFragment
        extends BaseFragment {

    public static final String TAG = "TocFragment";

    /** The Fragment ViewModel. */
    private ShowBookViewModel mVm;

    /** View Binding. */
    private FragmentTocBinding mVb;

    /** The Adapter. */
    @SuppressWarnings("FieldCanBeLocal")
    private TocEntryAdapter mAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(ShowBookViewModel.class);
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

        mVb.toc.setHasFixedSize(true);
        //noinspection ConstantConditions
        mVb.toc.addItemDecoration(
                new DividerItemDecoration(context, RecyclerView.VERTICAL));

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // Optional overlay
        final int overlayType = Prefs.getIntListPref(global,
                                                     Prefs.pk_booklist_fastscroller_overlay,
                                                     FastScroller.OverlayProvider.STYLE_MD2);
        FastScroller.attach(mVb.toc, overlayType);

        final List<TocEntry> tocList = mVm.getCurrentBook()
                                          .getParcelableArrayList(Book.BKEY_TOC_LIST);
        mAdapter = new TocEntryAdapter(context, tocList);
        mVb.toc.setAdapter(mAdapter);
    }

    private static class TocEntryAdapter
            extends RecyclerView.Adapter<TocEntryAdapter.Holder>
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
        TocEntryAdapter(@NonNull final Context context,
                        @NonNull final List<TocEntry> tocList) {
            mInflater = LayoutInflater.from(context);
            mTocList = tocList;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowTocEntryWithAuthorBinding vb = RowTocEntryWithAuthorBinding
                    .inflate(mInflater, parent, false);
            return new Holder(vb);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final TocEntry tocEntry = mTocList.get(position);
            final Context context = mInflater.getContext();

            holder.vb.title.setText(tocEntry.getLabel(context));
            holder.vb.author.setText(tocEntry.getPrimaryAuthor().getLabel(context));

            final boolean isSet = tocEntry.getBookCount() > 1;
            if (isSet) {
                holder.vb.cbxMultipleBooks.setVisibility(View.VISIBLE);
                holder.vb.cbxMultipleBooks.setOnClickListener(v -> {
                    final String titles = tocEntry
                            .getBookTitles()
                            .stream()
                            .map(bt -> context.getString(R.string.list_element, bt.second))
                            .collect(Collectors.joining("\n"));
                    StandardDialogs.infoPopup(
                            holder.vb.cbxMultipleBooks,
                            context.getString(R.string.lbl_story_in_multiple_books, titles));
                });
            }

            final PartialDate date = tocEntry.getFirstPublicationDate();
            if (date.isEmpty()) {
                holder.vb.year.setVisibility(View.GONE);
            } else {
                holder.vb.year.setVisibility(View.VISIBLE);
                // show full date string (if available)
                holder.vb.year.setText(context.getString(R.string.brackets, date.getIsoString()));
            }
        }

//        @Override
//        public int getItemViewType(final int position) {
//            return mTocList.get(position).getType();
//        }

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

            private final RowTocEntryWithAuthorBinding vb;

            Holder(@NonNull final RowTocEntryWithAuthorBinding vb) {
                super(vb.getRoot());
                this.vb = vb;
            }
        }
    }
}

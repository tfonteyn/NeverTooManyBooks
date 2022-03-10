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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public abstract class TocBaseAdapter
        extends RecyclerView.Adapter<TocBaseAdapter.TocHolder>
        implements FastScroller.PopupTextProvider {

    /** Cached inflater. */
    protected final LayoutInflater mInflater;
    @NonNull
    protected final List<AuthorWork> mAuthorWorkList;
    private final String mBookStr;
    private final Drawable mBookEntryIcon;
    private final String mMultipleBooksStr;
    private final Drawable mTocEntryIcon;

    /**
     * Constructor.
     *
     * @param context        Current context
     * @param authorWorkList to show
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocBaseAdapter(@NonNull final Context context,
                          @NonNull final List<AuthorWork> authorWorkList) {
        mInflater = LayoutInflater.from(context);
        mAuthorWorkList = authorWorkList;

        final Resources.Theme theme = context.getTheme();
        final Resources res = context.getResources();

        // The entry is an actual book
        mBookStr = res.getString(R.string.lbl_book);
        mBookEntryIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
        // The entry is a story (etc...) which appears in multiple books.
        mMultipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
        mTocEntryIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
    }

    protected void initTypeButton(@NonNull final ImageButton btnType,
                                  final int viewType) {
        if (viewType == AuthorWork.Type.TocEntry.value) {
            btnType.setImageDrawable(mTocEntryIcon);
            btnType.setContentDescription(mMultipleBooksStr);
        } else {
            btnType.setImageDrawable(mBookEntryIcon);
            btnType.setContentDescription(mBookStr);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final TocHolder holder,
                                 final int position) {

        final Context context = mInflater.getContext();

        final AuthorWork work = mAuthorWorkList.get(position);

        // No icon for TocEntry which appear in a single book
        if (work.getWorkType() == AuthorWork.Type.TocEntry && work.getBookCount() <= 1) {
            holder.getIconBtnView().setVisibility(View.INVISIBLE);
        } else {
            holder.getIconBtnView().setVisibility(View.VISIBLE);
        }

        holder.getTitleView().setText(work.getLabel(context));

        final PartialDate date = work.getFirstPublicationDate();
        if (date.isPresent()) {
            // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
            // cut the date to just the year.
            final String fp = context.getString(R.string.brackets,
                                                String.valueOf(date.getYearValue()));
            holder.getFirstPublicationView().setText(fp);
            holder.getFirstPublicationView().setVisibility(View.VISIBLE);
        } else {
            holder.getFirstPublicationView().setVisibility(View.GONE);
        }

        final TextView authorView = holder.getAuthorView();
        if (authorView != null) {
            final Author author = work.getPrimaryAuthor();
            if (author != null) {
                authorView.setText(author.getLabel(mInflater.getContext()));
            }
        }
    }

    @Override
    public int getItemViewType(final int position) {
        final AuthorWork work = mAuthorWorkList.get(position);
        return work.getWorkType().value;
    }

    @Override
    public int getItemCount() {
        return mAuthorWorkList.size();
    }

    @NonNull
    @Override
    public String[] getPopupText(final int position) {
        return new String[]{mAuthorWorkList.get(position).getLabel(mInflater.getContext())};
    }

    public abstract static class TocHolder
            extends RecyclerView.ViewHolder {

        public TocHolder(@NonNull final View itemView) {
            super(itemView);
        }

        @NonNull
        public abstract ImageButton getIconBtnView();

        @NonNull
        public abstract TextView getTitleView();

        @NonNull
        public abstract TextView getFirstPublicationView();

        @Nullable
        public TextView getAuthorView() {
            return null;
        }
    }
}

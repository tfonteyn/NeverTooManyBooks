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
    protected final List<AuthorWork> mTocList;
    private final String mBookStr;
    private final Drawable mBookEntryIcon;
    private final String mMultipleBooksStr;
    private final Drawable mTocEntryIcon;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param tocList to show
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocBaseAdapter(@NonNull final Context context,
                          @NonNull final List<AuthorWork> tocList) {
        mInflater = LayoutInflater.from(context);
        mTocList = tocList;

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
        switch (viewType) {
            case AuthorWork.TYPE_TOC: {
                btnType.setImageDrawable(mTocEntryIcon);
                btnType.setContentDescription(mMultipleBooksStr);
                break;
            }
            case AuthorWork.TYPE_BOOK: {
                btnType.setImageDrawable(mBookEntryIcon);
                btnType.setContentDescription(mBookStr);
                break;
            }
            default: {
                // we should never get here... flw
                break;
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final TocHolder holder,
                                 final int position) {

        final Context context = mInflater.getContext();

        final AuthorWork work = mTocList.get(position);

        // Show the icon if it's a TocEntry and appears in more than one book
        // in our collection, or if this entry is a Book
        final char workType = work.getWorkType();
        if ((workType == AuthorWork.TYPE_TOC && work.getBookCount() > 1)
            || (workType == AuthorWork.TYPE_BOOK)) {
            holder.getIconBtnView().setVisibility(View.VISIBLE);
        } else {
            holder.getIconBtnView().setVisibility(View.INVISIBLE);
        }

        holder.getTitleView().setText(work.getLabel(context));

        final PartialDate date = work.getFirstPublicationDate();
        if (date.isEmpty()) {
            holder.getFirstPublicationView().setVisibility(View.GONE);
        } else {
            // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
            // cut the date to just the year.
            final String fp = context.getString(R.string.brackets,
                                                String.valueOf(date.getYearValue()));
            holder.getFirstPublicationView().setText(fp);
            holder.getFirstPublicationView().setVisibility(View.VISIBLE);
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
        final AuthorWork work = mTocList.get(position);
        return work.getWorkType();
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

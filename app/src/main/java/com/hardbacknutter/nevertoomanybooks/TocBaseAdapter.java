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
import java.util.stream.Collectors;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public abstract class TocBaseAdapter
        extends RecyclerView.Adapter<TocBaseAdapter.TocHolder>
        implements FastScroller.PopupTextProvider {

    /** Cached inflater. */
    protected final LayoutInflater inflater;
    @NonNull
    protected final List<AuthorWork> authorWorkList;
    private final String bookStr;
    private final Drawable bookEntryIcon;
    private final String multipleBooksStr;
    private final Drawable tocEntryIcon;

    /**
     * Constructor.
     *
     * @param context        Current context
     * @param authorWorkList to show
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocBaseAdapter(@NonNull final Context context,
                          @NonNull final List<AuthorWork> authorWorkList) {
        inflater = LayoutInflater.from(context);
        this.authorWorkList = authorWorkList;

        final Resources.Theme theme = context.getTheme();
        final Resources res = context.getResources();

        // The entry is an actual book
        bookStr = res.getString(R.string.lbl_book);
        bookEntryIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
        // The entry is a story (etc...) which appears in multiple books.
        multipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
        tocEntryIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
    }

    protected void initTypeButton(@NonNull final TocHolder holder,
                                  final int viewType) {
        final ImageButton btnType = holder.getIconBtnView();

        if (viewType == AuthorWork.Type.TocEntry.value) {
            btnType.setImageDrawable(tocEntryIcon);
            btnType.setContentDescription(multipleBooksStr);

            btnType.setOnClickListener(v -> {
                final Context context = v.getContext();
                final String titles = authorWorkList
                        .get(holder.getBindingAdapterPosition())
                        .getBookTitles(context)
                        .stream()
                        .map(bt -> context.getString(R.string.list_element,
                                                     bt.getLabel(context)))
                        .collect(Collectors.joining("\n"));
                final String msg = context.getString(R.string.lbl_story_in_multiple_books,
                                                     holder.getTitleView().getText().toString(),
                                                     titles);
                // x/y offsets more or less arbitrary
                StandardDialogs.infoPopup(btnType, 24, -160, msg);
            });
        } else {
            // AuthorWork.Type.Book
            // AuthorWork.Type.BookLight
            btnType.setImageDrawable(bookEntryIcon);
            btnType.setContentDescription(bookStr);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final TocHolder holder,
                                 final int position) {

        final Context context = inflater.getContext();

        final AuthorWork work = authorWorkList.get(position);

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
                authorView.setText(author.getLabel(inflater.getContext()));
            }
        }
    }

    @Override
    public int getItemViewType(final int position) {
        final AuthorWork work = authorWorkList.get(position);
        return work.getWorkType().value;
    }

    @Override
    public int getItemCount() {
        return authorWorkList.size();
    }

    @NonNull
    @Override
    public String[] getPopupText(final int position) {
        return new String[]{authorWorkList.get(position).getLabel(inflater.getContext())};
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

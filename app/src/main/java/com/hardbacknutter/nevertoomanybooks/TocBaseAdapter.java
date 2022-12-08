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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.databinding.RowAuthorWorkBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public abstract class TocBaseAdapter
        extends RecyclerView.Adapter<TocBaseAdapter.Holder>
        implements FastScroller.PopupTextProvider {

    /** x/y offsets more or less arbitrary. */
    private static final int XOFF = 24;
    private static final int YOFF = -160;

    @NonNull
    protected final TocEntryHandler tocEntryHandler;

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    @NonNull
    private final List<AuthorWork> works;
    @Nullable
    private final Author mainAuthor;
    @NonNull
    private final String tocStr;
    @NonNull
    private final Drawable tocIcon;
    @NonNull
    private final String bookStr;
    @NonNull
    private final String multipleBooksStr;
    @NonNull
    private final Drawable bookIcon;
    @NonNull
    private final Drawable multipleBooksIcon;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param mainAuthor      the author who 'owns' the works list
     * @param works           to show
     * @param tocEntryHandler the handler to act on row clicks
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocBaseAdapter(@NonNull final Context context,
                          @Nullable final Author mainAuthor,
                          @NonNull final List<AuthorWork> works,
                          @NonNull final TocEntryHandler tocEntryHandler) {
        inflater = LayoutInflater.from(context);
        this.mainAuthor = mainAuthor;
        this.works = works;
        this.tocEntryHandler = tocEntryHandler;

        final Resources.Theme theme = context.getTheme();
        final Resources res = context.getResources();

        // The entry is a story (etc...) which appears in one book only.
        tocStr = res.getString(R.string.lbl_table_of_content_entry);
        tocIcon = res.getDrawable(R.drawable.ic_baseline_menu_book_24, theme);
        // The entry is a story (etc...) which appears in multiple books.
        multipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
        multipleBooksIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
        // The entry is an actual book
        bookStr = res.getString(R.string.lbl_book);
        bookIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);

    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final Holder holder = new Holder(RowAuthorWorkBinding
                                                 .inflate(inflater, parent, false));

        holder.vb.btnType.setOnClickListener(v -> onTypeButtonClicked(v, holder));

        return holder;
    }

    private void onTypeButtonClicked(@NonNull final View v,
                                     @NonNull final Holder holder) {
        final Context context = v.getContext();
        final AuthorWork work = works.get(holder.getBindingAdapterPosition());
        final String titles = work
                .getBookTitles(context)
                .stream()
                .map(bt -> context.getString(R.string.list_element,
                                             bt.getLabel(context)))
                .collect(Collectors.joining("\n"));

        if (work.getBookCount() > 1) {
            final String msg = context.getString(R.string.info_story_in_multiple_books,
                                                 holder.vb.title.getText().toString(),
                                                 titles);
            StandardDialogs.infoPopup(holder.vb.btnType, XOFF, YOFF, msg);
        } else {
            final String msg = context.getString(R.string.info_story_in_single_book,
                                                 holder.vb.title.getText().toString(),
                                                 titles);
            StandardDialogs.infoPopup(holder.vb.btnType, XOFF, YOFF, msg);
        }
    }

    @NonNull
    protected AuthorWork getWork(final int position) {
        return works.get(position);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        final Context context = inflater.getContext();

        final AuthorWork work = works.get(position);

        if (work.getWorkType() == AuthorWork.Type.TocEntry) {
            if (work.getBookCount() > 1) {
                holder.vb.btnType.setImageDrawable(multipleBooksIcon);
                holder.vb.btnType.setContentDescription(multipleBooksStr);
            } else {
                holder.vb.btnType.setImageDrawable(tocIcon);
                holder.vb.btnType.setContentDescription(tocStr);
            }
        } else {
            // AuthorWork.Type.Book
            // AuthorWork.Type.BookLight
            holder.vb.btnType.setImageDrawable(bookIcon);
            holder.vb.btnType.setContentDescription(bookStr);
        }

        holder.vb.title.setText(work.getLabel(context));

        final PartialDate date = work.getFirstPublicationDate();
        if (date.isPresent()) {
            // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
            // cut the date to just the year.
            final String fp = context.getString(R.string.brackets,
                                                String.valueOf(date.getYearValue()));
            holder.vb.year.setText(fp);
            holder.vb.year.setVisibility(View.VISIBLE);
        } else {
            holder.vb.year.setVisibility(View.GONE);
        }

        final Author primaryAuthor = works.get(position).getPrimaryAuthor();
        // only display a primary author for this work if its different from the main author
        if (primaryAuthor != null && !primaryAuthor.equals(mainAuthor)) {
            holder.vb.author.setVisibility(View.VISIBLE);
            holder.vb.author.setText(primaryAuthor.getLabel(inflater.getContext()));
        } else {
            holder.vb.author.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemViewType(final int position) {
        final AuthorWork work = works.get(position);
        return work.getWorkType().asChar();
    }

    @Override
    public int getItemCount() {
        return works.size();
    }

    @NonNull
    @Override
    public String[] getPopupText(final int position) {
        return new String[]{works.get(position).getLabel(inflater.getContext())};
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        public final RowAuthorWorkBinding vb;

        public Holder(@NonNull final RowAuthorWorkBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }
}

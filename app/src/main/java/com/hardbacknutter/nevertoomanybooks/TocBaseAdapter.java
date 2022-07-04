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

    /** Cached inflater. */
    @NonNull
    protected final LayoutInflater inflater;
    @NonNull
    protected final List<AuthorWork> works;
    @Nullable
    private final Author mainAuthor;
    @NonNull
    private final String bookStr;
    @NonNull
    private final Drawable bookEntryIcon;
    @NonNull
    private final String multipleBooksStr;
    @NonNull
    private final Drawable tocEntryIcon;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param mainAuthor the author who 'owns' the works list
     * @param works      to show
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocBaseAdapter(@NonNull final Context context,
                          @Nullable final Author mainAuthor,
                          @NonNull final List<AuthorWork> works) {
        inflater = LayoutInflater.from(context);
        this.mainAuthor = mainAuthor;
        this.works = works;

        final Resources.Theme theme = context.getTheme();
        final Resources res = context.getResources();

        // The entry is an actual book
        bookStr = res.getString(R.string.lbl_book);
        bookEntryIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
        // The entry is a story (etc...) which appears in multiple books.
        multipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
        tocEntryIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowAuthorWorkBinding hVb = RowAuthorWorkBinding
                .inflate(inflater, parent, false);
        final Holder holder = new Holder(hVb);
        initTypeButton(holder, viewType);

        return holder;
    }

    private void initTypeButton(@NonNull final Holder holder,
                                final int viewType) {

        if (viewType == AuthorWork.Type.TocEntry.value) {
            holder.vb.btnType.setImageDrawable(tocEntryIcon);
            holder.vb.btnType.setContentDescription(multipleBooksStr);

            holder.vb.btnType.setOnClickListener(v -> {
                final Context context = v.getContext();
                final String titles = works
                        .get(holder.getBindingAdapterPosition())
                        .getBookTitles(context)
                        .stream()
                        .map(bt -> context.getString(R.string.list_element,
                                                     bt.getLabel(context)))
                        .collect(Collectors.joining("\n"));
                final String msg = context.getString(R.string.lbl_story_in_multiple_books,
                                                     holder.vb.title.getText().toString(),
                                                     titles);
                // x/y offsets more or less arbitrary
                StandardDialogs.infoPopup(holder.vb.btnType, 24, -160, msg);
            });
        } else {
            // AuthorWork.Type.Book
            // AuthorWork.Type.BookLight
            holder.vb.btnType.setImageDrawable(bookEntryIcon);
            holder.vb.btnType.setContentDescription(bookStr);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        final Context context = inflater.getContext();

        final AuthorWork work = works.get(position);

        // No icon for TocEntry which appear in a single book
        if (work.getWorkType() == AuthorWork.Type.TocEntry && work.getBookCount() <= 1) {
            holder.vb.btnType.setVisibility(View.INVISIBLE);
        } else {
            holder.vb.btnType.setVisibility(View.VISIBLE);
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
        // only display a primary author for this work if its different
        // from the main author
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
        return work.getWorkType().value;
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

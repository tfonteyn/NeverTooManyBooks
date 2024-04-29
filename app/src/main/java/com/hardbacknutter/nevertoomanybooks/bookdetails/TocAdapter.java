/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.databinding.RowAuthorWorkBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;

public class TocAdapter
        extends RecyclerView.Adapter<TocAdapter.AuthorWorkHolder>
        implements FastScroller.PopupTextProvider {

    /** x/y offsets more or less arbitrary. */
    private static final int XOFF = 24;
    private static final int YOFF = -160;

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    @NonNull
    private final List<AuthorWork> works;
    @NonNull
    private final Style style;
    @NonNull
    private final List<Author> authors;
    @Nullable
    private OnRowClickListener rowClickListener;
    @Nullable
    private OnRowClickListener rowShowMenuListener;
    @Nullable
    private ExtMenuButton contextMenuMode;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   to use
     * @param authors the author who 'owns' the works list
     * @param works   to show
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public TocAdapter(@NonNull final Context context,
                      @NonNull final Style style,
                      @NonNull final List<Author> authors,
                      @NonNull final List<AuthorWork> works) {
        inflater = LayoutInflater.from(context);
        this.contextMenuMode = ExtMenuButton.getPreferredMode(context);
        this.style = style;
        this.authors = authors;
        this.works = works;
    }

    /**
     * Set the {@link OnRowClickListener} for a click on a row.
     *
     * @param listener to set
     */
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        this.rowClickListener = listener;
    }

    /**
     * Set the {@link OnRowClickListener} for showing the context menu on a row.
     *
     * @param contextMenuMode how to show context menus
     * @param listener        to receive clicks
     */
    public void setOnRowShowMenuListener(@NonNull final ExtMenuButton contextMenuMode,
                                         @Nullable final OnRowClickListener listener) {
        this.rowShowMenuListener = listener;
        this.contextMenuMode = contextMenuMode;
    }

    @NonNull
    @Override
    public AuthorWorkHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                               final int viewType) {
        final AuthorWorkHolder holder;
        switch (AuthorWork.Type.getType((char) viewType)) {
            case TocEntry: {
                final View view = inflater.inflate(R.layout.row_author_work, parent, false);
                holder = new TocEntryHolder(view, style);
                break;
            }
            case BookLight: {
                final View view = inflater.inflate(R.layout.row_author_work, parent, false);
                holder = new BookLightHolder(view, style);
                break;
            }
            case Book: {
                final View view = inflater.inflate(R.layout.row_author_work, parent, false);
                holder = new BookHolder(view, style);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(viewType));
        }

        holder.setOnRowClickListener(rowClickListener);
        holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final AuthorWorkHolder holder,
                                 final int position) {

        final AuthorWork work = works.get(position);

        holder.onBind(work);

        // only display a primary author for this work if different from the main author
        boolean same = false;
        if (!authors.isEmpty()) {
            same = Objects.equals(work.getPrimaryAuthor(), authors.get(0));
        }
        holder.vb.author.setVisibility(same ? View.GONE : View.VISIBLE);
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
        return new String[]{works.get(position)
                .getLabel(inflater.getContext(), Details.AutoSelect, style)};
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public static class TocEntryHolder
            extends AuthorWorkHolder {

        @NonNull
        private final String tocStr;
        @NonNull
        private final Drawable tocIcon;

        @NonNull
        private final String multipleBooksStr;
        @NonNull
        private final Drawable multipleBooksIcon;

        TocEntryHolder(@NonNull final View itemView,
                       @NonNull final Style style) {
            super(itemView, style);
            final Context context = itemView.getContext();
            final Resources.Theme theme = context.getTheme();
            final Resources res = context.getResources();

            // The entry is a story (etc...) which appears in one book only.
            tocStr = res.getString(R.string.lbl_table_of_content_entry);
            tocIcon = res.getDrawable(R.drawable.ic_baseline_menu_book_24, theme);
            // The entry is a story (etc...) which appears in multiple books.
            multipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
            multipleBooksIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
        }

        @Override
        public void onBind(@NonNull final AuthorWork work) {
            super.onBind(work);
            if (work.getBookCount() > 1) {
                vb.btnType.setIcon(multipleBooksIcon);
                vb.btnType.setContentDescription(multipleBooksStr);
            } else {
                vb.btnType.setIcon(tocIcon);
                vb.btnType.setContentDescription(tocStr);
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public static class BookLightHolder
            extends AuthorWorkHolder {

        @NonNull
        private final String typeDescription;

        @NonNull
        private final Drawable typeIcon;

        BookLightHolder(@NonNull final View itemView,
                        @NonNull final Style style) {
            super(itemView, style);
            final Context context = itemView.getContext();
            final Resources.Theme theme = context.getTheme();
            final Resources res = context.getResources();
            typeDescription = res.getString(R.string.lbl_book);
            typeIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
        }

        @Override
        public void onBind(@NonNull final AuthorWork work) {
            super.onBind(work);
            vb.btnType.setIcon(typeIcon);
            vb.btnType.setContentDescription(typeDescription);
        }
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    public static class BookHolder
            extends AuthorWorkHolder {

        @NonNull
        private final String typeDescription;

        @NonNull
        private final Drawable typeIcon;

        BookHolder(@NonNull final View itemView,
                   @NonNull final Style style) {
            super(itemView, style);
            final Context context = itemView.getContext();
            final Resources.Theme theme = context.getTheme();
            final Resources res = context.getResources();
            typeDescription = res.getString(R.string.lbl_book);
            typeIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
        }

        @Override
        public void onBind(@NonNull final AuthorWork work) {
            super.onBind(work);
            vb.btnType.setIcon(typeIcon);
            vb.btnType.setContentDescription(typeDescription);
        }
    }

    public static class AuthorWorkHolder
            extends RowViewHolder
            implements BindableViewHolder<AuthorWork> {

        @NonNull
        final RowAuthorWorkBinding vb;
        @NonNull
        private final Style style;

        AuthorWorkHolder(@NonNull final View itemView,
                         @NonNull final Style style) {
            super(itemView);
            vb = RowAuthorWorkBinding.bind(itemView);
            this.style = style;
        }

        @Override
        public void onBind(@NonNull final AuthorWork work) {

            final Context context = itemView.getContext();
            vb.title.setText(work.getLabel(context, Details.AutoSelect, style));

            final PartialDate date = work.getFirstPublicationDate();
            if (date.isPresent()) {
                // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
                // cut the date to just the year.
                final String fp = context.getString(R.string.brackets,
                                                    String.valueOf(date.getYearValue()));
                vb.year.setText(fp);
                vb.year.setVisibility(View.VISIBLE);
            } else {
                vb.year.setVisibility(View.GONE);
            }

            final Author primaryAuthor = work.getPrimaryAuthor();
            vb.author.setText(primaryAuthor != null
                              //TODO: maybe add support for real-name by using Details.Full
                              // however... screen space is at a premium: use Details.Normal
                              ? primaryAuthor.getLabel(context, Details.Normal, style)
                              : null);

            vb.btnType.setOnClickListener(anchor -> {
                final String titles = work
                        .getBookTitles(context)
                        .stream()
                        .map(bt -> context
                                .getString(R.string.list_element,
                                           bt.getLabel(context, Details.AutoSelect, style)))
                        .collect(Collectors.joining("\n"));

                if (work.getBookCount() > 1) {
                    final String msg = context
                            .getString(R.string.info_story_in_multiple_books,
                                       work.getLabel(context, Details.AutoSelect, style),
                                       titles);
                    StandardDialogs.infoPopup(anchor, XOFF, YOFF, msg);
                } else {
                    final String msg = context
                            .getString(R.string.info_story_in_single_book,
                                       work.getLabel(context, Details.AutoSelect, style),
                                       titles);
                    StandardDialogs.infoPopup(anchor, XOFF, YOFF, msg);
                }
            });
        }
    }
}

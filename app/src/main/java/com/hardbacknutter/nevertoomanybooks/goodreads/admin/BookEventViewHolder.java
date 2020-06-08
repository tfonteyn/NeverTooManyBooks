/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.admin;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.goodreads.events.SendBookEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQEventCursorRow;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class BookEventViewHolder
        extends BaseViewHolder {

    private final TextView titleView;
    private final TextView authorView;
    private final Button retryButton;
    private final TextView errorView;
    private final TextView infoView;

    @SuppressWarnings("FieldCanBeLocal")
    private final CompoundButton checkedButton;

    public BookEventViewHolder(@NonNull final View itemView) {
        super(itemView);

        titleView = itemView.findViewById(R.id.title);
        authorView = itemView.findViewById(R.id.author);
        retryButton = itemView.findViewById(R.id.btn_retry);
        errorView = itemView.findViewById(R.id.error);
        infoView = itemView.findViewById(R.id.info);

        // not used for now
        checkedButton = itemView.findViewById(R.id.cbx_selected);
        checkedButton.setVisibility(View.GONE);
    }

    public void bind(@NonNull final TQEventCursorRow rowData,
                     @NonNull final SendBookEvent event,
                     @NonNull final DAO db) {
        final Context context = itemView.getContext();
        final Locale userLocale = LocaleUtils.getUserLocale(context);

        final long bookId = event.getBookId();

        String title = db.getBookTitle(bookId);
        if (title == null) {
            title = context.getString(R.string.warning_book_no_longer_exists);
        }

        final ArrayList<Author> authors = db.getAuthorsByBookId(bookId);
        final String authorName;
        if (!authors.isEmpty()) {
            authorName = Author.getCondensedNames(context, authors);
        } else {
            authorName = context.getString(R.string.unknown).toUpperCase(userLocale);
        }

        titleView.setText(title);
        authorView.setText(authorName);
        errorView.setText(event.getDescription(context));
        infoView.setText(infoView.getContext().getString(
                R.string.gr_tq_occurred_at, toPrettyDateTime(rowData.getEventDate(), userLocale)));

        final String isbn = db.getBookIsbn(bookId);
        if (isbn != null && !isbn.isEmpty()) {
            retryButton.setVisibility(View.VISIBLE);
            retryButton.setOnClickListener(v -> event.retry(v.getContext()));
        } else {
            retryButton.setVisibility(View.GONE);
        }
    }
}

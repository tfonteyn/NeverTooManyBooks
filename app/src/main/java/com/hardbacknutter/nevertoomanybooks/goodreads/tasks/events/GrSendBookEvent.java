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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks.events;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.EditBookActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemViewHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.ContextDialogItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.Event;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.EventsCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookLegacyTask;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * All Event objects resulting from sending books to Goodreads.
 */
public class GrSendBookEvent
        extends Event<EventsCursor, GrSendBookEvent.BookEventViewHolder> {

    private static final long serialVersionUID = 6080239141221330342L;
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param description Description of this event.
     * @param bookId      ID of related book.
     */
    GrSendBookEvent(@NonNull final String description,
                    final long bookId) {
        super(description);
        mBookId = bookId;
    }

    /**
     * Get the related Book ID.
     *
     * @return id of related book.
     */
    long getBookId() {
        return mBookId;
    }

    /**
     * Resubmit this book and delete this event.
     *
     * @param context Current context
     */
    private void retry(@NonNull final Context context) {
        QueueManager qm = QueueManager.getQueueManager();
        SendOneBookLegacyTask task = new SendOneBookLegacyTask(
                context.getString(R.string.gr_send_book_to_goodreads, mBookId),
                mBookId);
        qm.enqueueTask(task, QueueManager.Q_SMALL_JOBS);
        qm.deleteEvent(getId());
    }

    @Override
    @NonNull
    public BookEventViewHolder onCreateViewHolder(@NonNull final LayoutInflater layoutInflater,
                                                  @NonNull final ViewGroup parent) {
        View itemView = layoutInflater.inflate(R.layout.row_event_info, parent, false);
        return new BookEventViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final BookEventViewHolder holder,
                                 @NonNull final EventsCursor row,
                                 @NonNull final DAO db) {

        Context context = holder.itemView.getContext();
        Locale userLocale = LocaleUtils.getUserLocale(context);

        ArrayList<Author> authors = db.getAuthorsByBookId(mBookId);
        String authorName;
        if (!authors.isEmpty()) {
            authorName = authors.get(0).getLabel(context);
            if (authors.size() > 1) {
                authorName = context.getString(R.string.and_others, authorName);
            }
        } else {
            authorName = context.getString(R.string.unknown).toUpperCase(userLocale);
        }

        String title = db.getBookTitle(mBookId);
        if (title == null) {
            title = context.getString(R.string.warning_book_no_longer_exists);
        }

        holder.titleView.setText(title);
        holder.authorView.setText(context.getString(R.string.lbl_by_author_s, authorName));
        holder.errorView.setText(getDescription(context));

        String date = DateUtils.toPrettyDateTime(row.getEventDate(), userLocale);
        holder.dateView.setText(context.getString(R.string.gr_tq_occurred_at, date));

        holder.checkedButton.setChecked(row.isSelected());
        holder.checkedButton.setOnCheckedChangeListener(
                (v, isChecked) -> row.setSelected(getId(), isChecked));

        String isbn = db.getBookIsbn(mBookId);
        if (isbn != null && !isbn.isEmpty()) {
            holder.retryButton.setVisibility(View.VISIBLE);
            holder.retryButton.setOnClickListener(v -> retry(v.getContext()));
        } else {
            holder.retryButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final DAO db) {

        // EDIT BOOK
        menuItems
                .add(new ContextDialogItem(context.getString(R.string.action_edit_ellipsis), () -> {
            Intent intent = new Intent(context, EditBookActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, mBookId);
            context.startActivity(intent);
        }));

        // ENHANCE: Reinstate Goodreads search when Goodreads work.editions is available
//            // SEARCH GOODREADS
//            menuItems.add(new ContextDialogItem(
//                    context.getString(R.string.progress_msg_searching_site,
//                                      context.getString(R.string.site_goodreads)), () -> {
//                Intent intent = new Intent(context, GoodreadsSearchActivity.class)
//                        .putExtra(DBDefinitions.KEY_PK_ID, getId());
//                context.startActivity(intent);
//            }));

        // DELETE EVENT
        menuItems.add(new ContextDialogItem(
                context.getString(R.string.gr_tq_menu_delete_event), () ->
                QueueManager.getQueueManager().deleteEvent(getId())));

        // RETRY EVENT
        String isbn = db.getBookIsbn(mBookId);
        if (isbn != null && !isbn.isEmpty()) {
            menuItems.add(new ContextDialogItem(context.getString(R.string.action_retry), () -> {
                retry(context);
                QueueManager.getQueueManager().deleteEvent(getId());
            }));
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "GrSendBookEvent{"
               + "super=" + super.toString()
               + "mBookId=" + mBookId
               + '}';
    }

    /**
     * Class to implement the holder model for view we create.
     */
    static class BookEventViewHolder
            extends BindableItemViewHolder {

        final TextView titleView;
        final TextView authorView;
        final TextView errorView;
        final TextView dateView;

        final CompoundButton checkedButton;
        final Button retryButton;

        BookEventViewHolder(@NonNull final View itemView) {
            super(itemView);

            titleView = itemView.findViewById(R.id.title);
            authorView = itemView.findViewById(R.id.author);
            checkedButton = itemView.findViewById(R.id.cbx_selected);
            dateView = itemView.findViewById(R.id.date);
            errorView = itemView.findViewById(R.id.error);
            retryButton = itemView.findViewById(R.id.btn_retry);

            // not used for now
            checkedButton.setVisibility(View.GONE);
        }
    }
}

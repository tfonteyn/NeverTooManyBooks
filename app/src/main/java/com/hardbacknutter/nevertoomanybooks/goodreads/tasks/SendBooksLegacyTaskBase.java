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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.EditBookActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.NotFoundException;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemViewHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.ContextDialogItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.Event;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.EventsCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHelper;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
abstract class SendBooksLegacyTaskBase
        extends TQTask {

    private static final long serialVersionUID = 5950331916209877890L;

    /** wait time before declaring network failure. */
    private static final int FIVE_MINUTES = 300;
    /** Number of books with no ISBN. */
    int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    int mNotFound;
    /** Number of books successfully sent. */
    int mSent;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    SendBooksLegacyTaskBase(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager) {
        Context context = App.getLocalizedAppContext();
        try {
            NetworkUtils.poke(context,
                              GoodreadsHandler.BASE_URL,
                              GoodreadsSearchEngine.SOCKET_TIMEOUT_MS);

            GoodreadsAuth grAuth = new GoodreadsAuth(new SettingsHelper(context));
            GoodreadsHandler apiHandler = new GoodreadsHandler(grAuth);
            if (grAuth.hasValidCredentials(context)) {
                return send(queueManager, context, apiHandler);
            }
        } catch (@NonNull final IOException ignore) {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
        }

        return false;
    }

    /**
     * @param context    Current context
     * @param apiHandler the Goodreads Manager
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    protected abstract boolean send(@NonNull QueueManager queueManager,
                                    @NonNull Context context,
                                    @NonNull GoodreadsHandler apiHandler);

    /**
     * Try to export one book.
     *
     * @param context    Current context
     * @param apiHandler the Goodreads Manager
     * @param db         Database Access
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsHandler apiHandler,
                        @NonNull final DAO db,
                        @NonNull final CursorRow cursorRow) {

        GrStatus result;
        try {
            result = apiHandler.sendOneBook(context, db, cursorRow);

        } catch (@NonNull final CredentialsException e) {
            setException(e);
            result = GrStatus.CredentialsError;
        } catch (@NonNull final NotFoundException e) {
            setException(e);
            result = GrStatus.NotFound;
        } catch (@NonNull final IOException e) {
            setException(e);
            result = GrStatus.IOError;
        } catch (@NonNull final RuntimeException e) {
            setException(e);
            result = GrStatus.UnexpectedError;
        }

        long bookId = cursorRow.getLong(DBDefinitions.KEY_PK_ID);

        switch (result) {
            case BookSent:
                // Record the change
                db.setGoodreadsSyncDate(bookId);
                mSent++;
                return true;

            case NoIsbn:
                storeEvent(new GrNoIsbnEvent(context, bookId));
                mNoIsbn++;
                // not a success, but don't try again until the user acts on the stored event
                return true;

            case NotFound:
                storeEvent(new GrNoMatchEvent(context, bookId));
                mNotFound++;
                // not a success, but don't try again until the user acts on the stored event
                return true;

            case IOError:
                // wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;

            case AuthorizationAlreadyGranted:
            case AuthorizationSuccessful:
            case AuthorizationFailed:
            case AuthorizationNeeded:
            case CredentialsMissing:
            case CredentialsError:
            case TaskQueuedWithSuccess:
            case ImportTaskAlreadyQueued:
            case ExportTaskAlreadyQueued:
            case Cancelled:
            case NoInternet:
            case UnexpectedError:
            default:
                queueManager.updateTask(this);
                return false;
        }
    }

    /**
     * All Event objects resulting from sending books to Goodreads.
     */
    private static class GrSendBookEvent
            extends Event {

        private static final long serialVersionUID = 6243512830928679140L;
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
        void retry(@NonNull final Context context) {
            QueueManager qm = QueueManager.getQueueManager();
            SendOneBookLegacyTask task = new SendOneBookLegacyTask(
                    context.getString(R.string.gr_send_book_to_goodreads, mBookId),
                    mBookId);
            qm.enqueueTask(task, QueueManager.Q_SMALL_JOBS);
            qm.deleteEvent(getId());
        }

        @Override
        @NonNull
        public BindableItemViewHolder onCreateViewHolder(@NonNull final ViewGroup parent) {
            View itemView = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.row_event_info, parent, false);
            return new BookEventViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final BindableItemViewHolder bindableItemViewHolder,
                                     @NonNull final BindableItemCursor cursor,
                                     @NonNull final DAO db) {

            BookEventViewHolder holder = (BookEventViewHolder) bindableItemViewHolder;
            EventsCursor eventsCursor = (EventsCursor) cursor;

            Context context = holder.itemView.getContext();
            Locale userLocale = LocaleUtils.getUserLocale(context);

            ArrayList<Author> authors = db.getAuthorsByBookId(mBookId);
            String authorName;
            if (!authors.isEmpty()) {
                authorName = authors.get(0).getLabel(context);
                if (authors.size() > 1) {
                    authorName = authorName + ' ' + context.getString(R.string.and_others);
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

            String date = DateUtils.toPrettyDateTime(eventsCursor.getEventDate(), userLocale);
            holder.dateView.setText(context.getString(R.string.gr_tq_occurred_at, date));

            holder.checkedButton.setChecked(eventsCursor.isSelected());
            holder.checkedButton.setTag(R.id.TAG_ITEM, this);
            holder.checkedButton.setOnCheckedChangeListener((v, isChecked) -> {
                //noinspection TypeMayBeWeakened
                GrSendBookEvent event = (GrSendBookEvent) v.getTag(R.id.TAG_ITEM);
                eventsCursor.setSelected(event.getId(), isChecked);
            });

            String isbn = db.getBookIsbn(mBookId);
            if (isbn != null && !isbn.isEmpty()) {
                holder.retryButton.setVisibility(View.VISIBLE);
                holder.retryButton.setTag(R.id.TAG_ITEM, this);
                holder.retryButton.setOnClickListener(v -> {
                    GrSendBookEvent event = (GrSendBookEvent) v.getTag(R.id.TAG_ITEM);
                    event.retry(v.getContext());
                });
            } else {
                holder.retryButton.setVisibility(View.GONE);
            }
        }

        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final List<ContextDialogItem> menuItems,
                                        @NonNull final DAO db) {

            // EDIT BOOK
            menuItems.add(new ContextDialogItem(context.getString(R.string.menu_edit), () -> {
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
                menuItems.add(new ContextDialogItem(context.getString(R.string.retry), () -> {
                    retry(context);
                    QueueManager.getQueueManager().deleteEvent(getId());
                }));
            }
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

    /**
     * Exception indicating the book's ISBN could not be found at Goodreads.
     */
    private static class GrNoMatchEvent
            extends GrSendBookEvent
            implements TipManager.TipOwner {

        private static final long serialVersionUID = 7618084144814445823L;

        GrNoMatchEvent(@NonNull final Context context,
                       final long bookId) {
            super(context.getString(R.string.warning_no_matching_book_found), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_match;
        }
    }

    /**
     * Exception indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends GrSendBookEvent
            implements TipManager.TipOwner {

        private static final long serialVersionUID = -8615058337902218680L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
            super(context.getString(R.string.warning_no_isbn_stored_for_book), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_isbn;
        }
    }
}

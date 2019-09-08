/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Base class for tasks. This builds and populates simple View objects to display the task.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class TQTask
        extends Task
        implements BindableItemCursorAdapter.BindableItem {

    private static final long serialVersionUID = -937739402397246913L;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    protected TQTask(@NonNull final String description) {
        super(description);
    }

    /**
     * @return {@code false} to requeue, {@code true} for success
     */
    public abstract boolean run(@NonNull QueueManager queueManager);

    @Override
    @NonNull
    public View getView(@NonNull final Context context,
                        @NonNull final BindableItemCursor cursor,
                        @NonNull final ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_task_info, parent, false);
        view.setTag(R.id.TAG_GR_TASK, this);

        TaskHolder holder = new TaskHolder(view);
        holder.checkButton.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);

        return view;
    }

    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final DAO db) {
        TaskHolder holder = (TaskHolder) view.getTag(R.id.TAG_GR_TASK_HOLDER);
        TasksCursor tasksCursor = (TasksCursor) cursor;

        Locale locale = LocaleUtils.getLocale(context);

        // Update task info binding
        holder.description.setText(getDescription(context));
        String statusCode = tasksCursor.getStatusCode().toUpperCase(Locale.ENGLISH);
        String statusText;
        switch (statusCode) {
            case STATUS_COMPLETE:
                statusText = context.getString(R.string.gr_tq_completed);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;

            case STATUS_FAILED:
                statusText = context.getString(R.string.gr_tq_failed);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.VISIBLE);
                break;

            case STATUS_QUEUED:
                statusText = context.getString(R.string.gr_tq_queued);
                String date = DateUtils.toPrettyDateTime(locale, tasksCursor.getRetryDate());
                holder.retry_info.setText(context.getString(R.string.gr_tq_retry_x_of_y_next_at_z,
                                                            getRetries(), getRetryLimit(), date));
                holder.retry_info.setVisibility(View.VISIBLE);
                holder.retryButton.setVisibility(View.GONE);
                break;

            default:
                statusText = context.getString(R.string.unknown);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;
        }

        statusText += context.getString(R.string.gr_tq_events_recorded, tasksCursor.getNoteCount());
        holder.state.setText(statusText);

        Exception e = getException();
        if (e != null) {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(context.getString(R.string.gr_tq_last_error_e,
                                                   e.getLocalizedMessage()));
        } else {
            holder.error.setVisibility(View.GONE);
        }
        //"Job id 123, Queued at 20 Jul 2012 17:50:23 GMT"
        String date = DateUtils.toPrettyDateTime(locale, tasksCursor.getQueuedDate());
        holder.job_info.setText(context.getString(R.string.gr_tq_generic_task_info, getId(), date));
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final View view,
                                    final long id,
                                    @NonNull final List<ContextDialogItem> items,
                                    @NonNull final DAO db) {

        ContextDialogItem item =
                new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_task),
                                      () -> QueueManager.getQueueManager().deleteTask(id));
        items.add(item);
    }

    /**
     * Holder to maintain task views.
     */
    static class TaskHolder {

        final TextView description;
        final TextView state;
        final TextView retry_info;
        final TextView error;
        final TextView job_info;
        final CompoundButton checkButton;
        final Button retryButton;

        TaskHolder(@NonNull final View view) {
            description = view.findViewById(R.id.description);
            state = view.findViewById(R.id.state);
            retry_info = view.findViewById(R.id.retry_info);
            error = view.findViewById(R.id.error);
            job_info = view.findViewById(R.id.job_info);
            checkButton = view.findViewById(R.id.cbx_checked);
            retryButton = view.findViewById(R.id.retry);

            view.setTag(R.id.TAG_GR_TASK_HOLDER, this);
        }
    }
}

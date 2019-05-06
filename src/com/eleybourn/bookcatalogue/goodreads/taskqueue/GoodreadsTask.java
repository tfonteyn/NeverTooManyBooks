/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Base class for tasks. This builds and populates simple View objects to display the task.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
public abstract class GoodreadsTask
        extends Task
        implements Serializable {


    private static final long serialVersionUID = -4388499226374546985L;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    protected GoodreadsTask(@NonNull final String description) {
        super(description);
    }

    /**
     * @return {@code false} to requeue, {@code true} for success
     */
    public abstract boolean run(@NonNull final QueueManager queueManager,
                                @NonNull final Context context);

    /**
     * Create a new View.
     */
    @Override
    @NonNull
    public View getView(@NonNull final LayoutInflater inflater,
                        @NonNull final BindableItemCursor cursor,
                        @NonNull final ViewGroup parent) {
        View view = inflater.inflate(R.layout.row_task_info, parent, false);
        view.setTag(R.id.TAG_GR_TASK, this);

        TaskHolder holder = new TaskHolder(view);
        holder.checkButton.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);

        return view;
    }

    /**
     * Bind task details to passed View.
     */
    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final DBA db) {
        TaskHolder holder = (TaskHolder) view.getTag(R.id.TAG_GR_TASK_HOLDER);
        TasksCursor tasksCursor = (TasksCursor) cursor;

        Locale locale = LocaleUtils.from(context);

        // Update task info binding
        holder.description.setText(getDescription(context));
        String statusCode = tasksCursor.getStatusCode().toUpperCase();
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
        //"Job ID 123, Queued at 20 Jul 2012 17:50:23 GMT"
        String date = DateUtils.toPrettyDateTime(locale, tasksCursor.getQueuedDate());
        holder.job_info.setText(context.getString(R.string.gr_tq_generic_task_info, getId(), date));
    }

    /**
     * Add context menu items.
     */
    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id,
                                    @NonNull final List<ContextDialogItem> items,
                                    @NonNull final DBA db) {

        items.add(new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_task),
                                        () -> QueueManager.getQueueManager().deleteTask(id))
        );
    }

    /**
     * Holder class record to maintain task views.
     *
     * @author Philip Warner
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
            checkButton = view.findViewById(R.id.checked);
            retryButton = view.findViewById(R.id.retry);

            view.setTag(R.id.TAG_GR_TASK_HOLDER, this);
        }
    }
}

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

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.Serializable;
import java.util.List;

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

    public GoodreadsTask(@NonNull final String description) {
        super(description);
    }

    /**
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    public abstract boolean run(@NonNull final QueueManager queueManager,
                                @NonNull final Context context);

    /**
     * Create a new View.
     */
    @Override
    public View newListItemView(@NonNull final Context context,
                                @NonNull final BindableItemCursor cursor,
                                @NonNull final ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.task_info, parent, false);
        ViewTagger.setTag(view, R.id.TAG_TASK, this);

        TaskHolder holder = new TaskHolder();
        holder.description = view.findViewById(R.id.description);
        holder.state = view.findViewById(R.id.state);
        holder.retry_info = view.findViewById(R.id.retry_info);
        holder.error = view.findViewById(R.id.error);
        holder.job_info = view.findViewById(R.id.job_info);
        holder.checkButton = view.findViewById(R.id.checked);
        holder.retryButton = view.findViewById(R.id.retry);

        ViewTagger.setTag(view, R.id.TAG_TASK_HOLDER, holder);

        ViewTagger.setTag(holder.checkButton, R.id.TAG_BOOK_EVENT_HOLDER, holder);

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
        TaskHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_TASK_HOLDER);
        TasksCursor tasksCursor = (TasksCursor) cursor;

        // Update task info binding
        holder.description.setText(this.getDescription());
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
                holder.retry_info
                        .setText(context.getString(R.string.gr_tq_retry_x_of_y_next_at_z,
                                                   this.getRetries(),
                                                   this.getRetryLimit(),
                                                   DateUtils.toPrettyDateTime(
                                                           tasksCursor.getRetryDate())));
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

        Exception e = this.getException();
        if (e != null) {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(BookCatalogueApp.getResString(R.string.gr_tq_last_error_e,
                                                               e.getLocalizedMessage()));
        } else {
            holder.error.setVisibility(View.GONE);
        }
        //"Job ID 123, Queued at 20 Jul 2012 17:50:23 GMT"
        holder.job_info.setText(BookCatalogueApp
                                        .getResString(R.string.gr_tq_generic_task_info,
                                                      this.getId(),
                                                      DateUtils.toPrettyDateTime(
                                                              tasksCursor.getQueuedDate())));
        //view.requestLayout();
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
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                QueueManager.getQueueManager().deleteTask(id);
                                            }
                                        })
        );
    }

    /**
     * Holder class record to maintain task views.
     *
     * @author Philip Warner
     */
    public static class TaskHolder {

        TextView description;
        TextView state;
        TextView retry_info;
        TextView error;
        TextView job_info;
        CompoundButton checkButton;
        Button retryButton;
    }
}

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
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.Serializable;
import java.util.List;

/**
 * Base class for tasks. This builds and populates simple View objects to display the task.
 *
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
public abstract class GoodreadsTask extends Task implements Serializable {
    private static final long serialVersionUID = -5985866222873741455L;

    private static final String STATUS_COMPLETE = "S";
    private static final String STATUS_FAILED = "F";
    private static final String STATUS_QUEUED = "Q";

    public GoodreadsTask(final @NonNull String description) {
        super(description);
    }

    /**
     * @return false to requeue, true for success
     */
    public abstract boolean run(final @NonNull QueueManager queueManager,
                                final @NonNull Context context);

    /**
     * Create a new View
     */
    @Override
    public View newListItemView(final @NonNull LayoutInflater inflater,
                                final @NonNull Context context,
                                final @NonNull BindableItemCursor cursor,
                                final @NonNull ViewGroup parent) {
        View view = inflater.inflate(R.layout.task_info, parent, false);
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
     * Bind task details to passed View
     */
    @Override
    public void bindView(final @NonNull View view,
                         final @NonNull Context context,
                         final @NonNull BindableItemCursor bindAbleCursor,
                         final @NonNull CatalogueDBAdapter db) {
        TaskHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_TASK_HOLDER);
        TasksCursor cursor = (TasksCursor) bindAbleCursor;

        // Update task info binding
        holder.description.setText(this.getDescription());
        String statusCode = cursor.getStatusCode().toUpperCase();
        String statusText;
        switch (statusCode) {
            case STATUS_COMPLETE:
                statusText = context.getString(R.string.completed);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;
            case STATUS_FAILED:
                statusText = context.getString(R.string.failed);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.VISIBLE);
                break;
            case STATUS_QUEUED:
                statusText = context.getString(R.string.queued);
                holder.retry_info.setText(
                        context.getString(R.string.retry_x_of_y_next_at_z,
                                this.getRetries(),
                                this.getRetryLimit(),
                                DateUtils.toPrettyDateTime(cursor.getRetryDate())));
                holder.retry_info.setVisibility(View.VISIBLE);
                holder.retryButton.setVisibility(View.GONE);
                break;
            default:
                statusText = context.getString(R.string.unknown);
                holder.retry_info.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;
        }

        statusText += context.getString(R.string.events_recorded, cursor.getNoteCount());
        holder.state.setText(statusText);

        Exception e = this.getException();
        if (e != null) {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(BookCatalogueApp.getResourceString(R.string.last_error_e, e.getLocalizedMessage()));
        } else {
            holder.error.setVisibility(View.GONE);
        }
        //"Job ID 123, Queued at 20 Jul 2012 17:50:23 GMT"
        holder.job_info.setText(BookCatalogueApp.getResourceString(R.string.generic_task_info,
                this.getId(),
                DateUtils.toPrettyDateTime(cursor.getQueuedDate())));
        //view.requestLayout();
    }

    /**
     * Add context menu items:
     * - Allow task deletion
     */
    @Override
    public void addContextMenuItems(final @NonNull Context context,
                                    final @NonNull AdapterView<?> parent,
                                    final @NonNull View view,
                                    final int position,
                                    final long id,
                                    final @NonNull List<ContextDialogItem> items,
                                    final @NonNull CatalogueDBAdapter db) {

        items.add(new ContextDialogItem(context.getString(R.string.menu_delete_task), new Runnable() {
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
    public class TaskHolder {
        TextView description;
        TextView state;
        TextView retry_info;
        TextView error;
        TextView job_info;
        CompoundButton checkButton;
        Button retryButton;
    }
}

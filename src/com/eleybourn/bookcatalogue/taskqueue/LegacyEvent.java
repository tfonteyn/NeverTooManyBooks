/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.taskqueue;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;

import java.util.ArrayList;

/**
 * Class to wrap events that can not be deserialized so that the EventsCursor *always* returns a valid Event.
 * 
 * @author Philip Warner
 */
public class LegacyEvent extends Event {

	private static final long serialVersionUID = -8518718598973561219L;
	private static final int TEXT_FIELD_1 = 1;
	private static final int TEXT_FIELD_2 = 2;
	private final byte[] m_original;

	public LegacyEvent(byte[] original, String description) {
		super(description);
		m_original = original;
	}

	@Override
	public View newListItemView(@NonNull LayoutInflater inflater, @NonNull Context context, @NonNull BindableItemCursor cursor, @NonNull ViewGroup parent)
	{
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(ViewGroup.MarginLayoutParams.MATCH_PARENT, ViewGroup.MarginLayoutParams.WRAP_CONTENT);
		TextView tv = new TextView(context);
		tv.setId(TEXT_FIELD_1);
		root.addView(tv, margins);
		tv = new TextView(context);
		tv.setId(TEXT_FIELD_2);
		root.addView(tv, margins);
		return root;
	}

	@Override
	public void bindView(@NonNull View view, @NonNull Context context, @NonNull BindableItemCursor cursor, @NonNull Object appInfo) {
		((TextView)view.findViewById(TEXT_FIELD_1)).setText("Legacy Event Placeholder for Event #" + this.getId());
		((TextView)view.findViewById(TEXT_FIELD_2)).setText("This event is obsolete and can not be recovered. It is probably advisable to delete it.");
	}

	public byte[] getOriginal() {
		return m_original;
	}

	@Override
	public void addContextMenuItems(@NonNull final Context ctx, @NonNull final AdapterView<?> parent,
									@NonNull final View v, final int position, final long id,
									@NonNull final ArrayList<ContextDialogItem> items,
									@NonNull final Object appInfo) {

		items.add(new ContextDialogItem(ctx.getString(R.string.delete_event), new Runnable() {
			@Override
			public void run() {
				QueueManager.getQueueManager().deleteEvent(LegacyEvent.this.getId());
			}
		}));

	}}

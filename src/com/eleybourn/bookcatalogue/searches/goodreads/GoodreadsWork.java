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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.lang.ref.WeakReference;

/**
 * Class to store the 'work' data returned via a goodreads search. It also creates a background task
 * to find images and waits for completion.
 * 
 * @author Philip Warner
 */
public class GoodreadsWork {
	public String title;
	public String imageUrl;
	public String smallImageUrl;
	public Long bookId;
	public Long workId;
	public Long pubDay;
	public Long pubMonth;
	public Long pubYear;
	public Double rating;
	public Long authorId;
	public String authorName;
	@Nullable
    private byte[] imageBytes = null;
	private GetImageTask mTask;
	@Nullable
    private WeakReference<ImageView> mImageView = null;

	public GoodreadsWork() {
		super();
	}

	/**
	 * Called in UI thread by background task when it has finished
	 */
	void handleTaskFinished(@NonNull final byte[] bytes) {
		imageBytes = bytes;

		final ImageView imageView = mImageView.get();
		if (imageView != null) {
			synchronized(imageView) {
				// Make sure our view is still associated with us
				if (this.equals(ViewTagger.getTag(imageView, R.id.TAG_GOODREADS_WORK))) {
					imageView.setImageBitmap(ImageUtils.getBitmapFromBytes(imageBytes) );
				}
			}						
		}
	}

	/**
	 * If the cover image has already been retrieved, put it in the passed view. Otherwise, request
	 * its retrieval and store a reference to the view for use when the image becomes available.
	 * 
	 * @param imageView		ImageView to display cover image
	 */
	void fillImageView(@NonNull final SimpleTaskQueue queue, @NonNull final ImageView imageView) {
		synchronized(this) {
			if (this.imageBytes == null) {
				// Image not retrieved yet, so clear any existing image
				imageView.setImageBitmap(null);
				// Save the view so we know where the image is going to be displayed
				mImageView = new WeakReference<>(imageView);
				// If we don't have a task already, start one.
				if (mTask == null) {
					// No task running, so Queue the task to get the image
					try {
						mTask = new GetImageTask(getBestUrl(), this);
						queue.enqueue(mTask);
					} catch (Exception e) {
						Logger.error(e, "Failed to create task to get image from goodreads");
					}
				}
				// Save the work in the View for verification
				ViewTagger.setTag(imageView, R.id.TAG_GOODREADS_WORK, this);
				//QueueManager.getQueueManager().bringTaskToFront(this.imageTaskId);
			} else {
				// We already have an image, so just expand it.
				imageView.setImageBitmap( ImageUtils.getBitmapFromBytes(this.imageBytes) );
				// Clear the work in the View, in case some other job was running
				ViewTagger.setTag(imageView, R.id.TAG_GOODREADS_WORK, null);
			}
		}
	}

	/**
	 * @return the 'best' (largest image) URL we have.
	 */
	private String getBestUrl() {
		if (imageUrl != null && !imageUrl.isEmpty()) {
			return imageUrl;
		} else {
			return smallImageUrl;
		}
	}
}

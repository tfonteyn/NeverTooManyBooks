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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

/**
 * Class to store the 'work' data returned via a Goodreads search.
 * It also creates a background task to find images and waits for completion.
 *
 * <pre>{@code
 * <work>
 *  <id type="integer">6872395</id>
 *  <books_count type="integer">3</books_count>
 *  <best_book_id type="integer">6677280</best_book_id>
 *  <reviews_count type="integer">28</reviews_count>
 *  <ratings_sum type="integer">54</ratings_sum>
 *  <ratings_count type="integer">15</ratings_count>
 *  <text_reviews_count type="integer">3</text_reviews_count>
 *  <original_publication_year type="integer">2009</original_publication_year>
 *  <original_publication_month type="integer">6</original_publication_month>
 *  <original_publication_day type="integer">1</original_publication_day>
 *  <original_title>
 *      Les pirates de Barataria, Tome 1 : Nouvelle Orléans
 *  </original_title>
 *  <original_language_id type="integer" nil="true"/>
 *  <media_type>book</media_type>
 *  <rating_dist>5:3|4:3|3:9|2:0|1:0|total:15</rating_dist>
 *  <desc_user_id type="integer">36471395</desc_user_id>
 *  <default_chaptering_book_id type="integer" nil="true"/>
 *  <default_description_language_code nil="true"/>
 *  <work_uri>kca://work/amzn1.gr.work.v1.Idamy7-vz0aK5n9u5hM9cA</work_uri>
 * </work>
 * }</pre>
 */
public class GoodreadsWork {

    public Long grBookId;
    public Long workId;

    public String title;
    public Long authorId;
    public String authorName;
    public String authorRole;

    public Long pubDay;
    public Long pubMonth;
    public Long pubYear;

    public Double rating;

    public String imageUrl;
    public String smallImageUrl;

    @Nullable
    private byte[] mImageBytes;
    private WeakReference<ImageView> mImageView;
    private int mMaxWidth;
    private int mMaxHeight;

    /**
     * If the cover image has already been retrieved, put it in the passed view.
     * Otherwise, request its retrieval and store a reference to the view for use when
     * the image becomes available.
     *
     * @param imageView ImageView to display cover image
     * @param maxWidth  Maximum width of the image
     * @param maxHeight Maximum height of the image
     */
    @UiThread
    void fillImageView(@NonNull final ImageView imageView,
                       final int maxWidth,
                       final int maxHeight) {
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
        synchronized (this) {
            if (mImageBytes == null) {
                // Image not retrieved yet, so clear any existing image
                ImageUtils.setPlaceholder(imageView, R.drawable.ic_image, 0, maxHeight);
                // Save the view so we know where the image is going to be displayed
                mImageView = new WeakReference<>(imageView);
                // run task to get the image. Use parallel executor.
                new GetImageTask(getBestUrl(), this)
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                // Save the work in the View for verification
                imageView.setTag(R.id.TAG_ITEM, this);

            } else {
                // We already have an image (but it could be empty!), so just expand it.
                if (mImageBytes.length != 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(mImageBytes, 0,
                                                                  mImageBytes.length,
                                                                  new BitmapFactory.Options());
                    ImageUtils.setImageView(imageView, bitmap, maxWidth, maxHeight, true);
                } else {
                    ImageUtils.setPlaceholder(imageView, R.drawable.ic_broken_image, 0,
                                              maxHeight);
                }

                // Clear the work in the View, in case some other job was running
                imageView.setTag(R.id.TAG_ITEM, null);
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

    /**
     * Called in UI thread by background task when it has finished.
     */
    @UiThread
    private void onGetImageTaskFinished(@NonNull final byte[] bytes) {
        mImageBytes = bytes;

        final ImageView imageView = mImageView.get();
        if (imageView != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (imageView) {
                // Make sure our view is still associated with us
                if (this.equals(imageView.getTag(R.id.TAG_ITEM))) {
                    if (mImageBytes.length != 0) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(mImageBytes, 0,
                                                                      mImageBytes.length,
                                                                      new BitmapFactory.Options());
                        ImageUtils.setImageView(imageView, bitmap, mMaxWidth, mMaxHeight, true);
                    } else {
                        ImageUtils.setPlaceholder(imageView, R.drawable.ic_broken_image, 0,
                                                  mMaxHeight);
                    }
                }
            }
        }
    }

    /**
     * Background task to load an image for a GoodreadsWork from a URL. Does not store it locally;
     * it will call the related Work when done.
     */
    static class GetImageTask
            extends AsyncTask<Void, Void, byte[]> {

        private static final String TAG = "GR.GetImageTask";
        /** URL of image to fetch. */
        @NonNull
        private final String mUrl;
        /** Related work. */
        @NonNull
        private final GoodreadsWork mWork;

        /**
         * Constructor.
         *
         * @param url to retrieve.
         */
        @UiThread
        GetImageTask(@NonNull final String url,
                     @NonNull final GoodreadsWork work) {
            mUrl = url;
            mWork = work;
        }

        /**
         * Just get the byte data of the image.
         * NOT a Bitmap because we fetch several and store them in the related
         * GoodreadsWork object and Bitmap objects are much larger than JPG objects.
         */
        @Override
        @WorkerThread
        protected byte[] doInBackground(final Void... params) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            return ImageUtils.getBytes(context, mUrl);
        }

        /**
         * Tell the {@link GoodreadsWork} about it.
         */
        @Override
        @UiThread
        protected void onPostExecute(final byte[] result) {
            mWork.onGetImageTaskFinished(result);
        }
    }
}

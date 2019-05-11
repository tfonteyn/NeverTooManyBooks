/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collection of utility functions used in this package.
 */
final class CropUtil {

    private CropUtil() {
    }

    /**
     * Compute the sample size as a function of minSideLength and maxNumOfPixels.
     * minSideLength is used to specify that minimal width or height of a bitmap.
     * maxNumOfPixels is used to specify the maximal size in pixels that are tolerable
     * in terms of memory usage.
     * <p>
     * The function returns a sample size based on the constraints. Both size and minSideLength
     * can be passed in as {@link CropIImage#UNCONSTRAINED}, which indicates no care of
     * the corresponding constraint.
     * The functions prefers returning a sample size that generates a smaller bitmap,
     * unless minSideLength = {@link CropIImage#UNCONSTRAINED}.
     */
    static Bitmap transform(@NonNull Matrix matrix,
                            @NonNull final Bitmap source,
                            final int targetWidth,
                            final int targetHeight,
                            final boolean scaleUp) {
        int deltaX = source.getWidth() - targetWidth;
        int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
             * In this case the bitmap is smaller, at least in one dimension,
             * than the target. Transform it by placing as much of the image as
             * possible into the target and leaving the top/bottom or left/right
             * (or both) black.
             */
            Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight,
                                            Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b2);

            int deltaXHalf = Math.max(0, deltaX / 2);
            int deltaYHalf = Math.max(0, deltaY / 2);
            Rect src = new Rect(deltaXHalf, deltaYHalf, deltaXHalf
                    + Math.min(targetWidth, source.getWidth()), deltaYHalf
                                        + Math.min(targetHeight, source.getHeight()));
            int dstX = (targetWidth - src.width()) / 2;
            int dstY = (targetHeight - src.height()) / 2;
            Rect dst = new Rect(dstX, dstY, targetWidth - dstX, targetHeight
                    - dstY);
            c.drawBitmap(source, src, dst, null);
            return b2;
        }
        float bitmapWidthF = source.getWidth();
        float bitmapHeightF = source.getHeight();

        float bitmapAspect = bitmapWidthF / bitmapHeightF;
        float viewAspect = (float) targetWidth / targetHeight;

        if (bitmapAspect > viewAspect) {
            float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                matrix.setScale(scale, scale);
            } else {
                matrix = null;
            }
        } else {
            float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                matrix.setScale(scale, scale);
            } else {
                matrix = null;
            }
        }

        Bitmap b1;
        if (matrix != null) {
            // this is used for mini thumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0, source.getWidth(),
                                     source.getHeight(), matrix, true);
        } else {
            b1 = source;
        }

        int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        Bitmap b2 = Bitmap.createBitmap(b1, dx1 / 2, dy1 / 2, targetWidth,
                                        targetHeight);

        if (b1 != source) {
            b1.recycle();
        }

        return b2;
    }

    static void startBackgroundJob(@NonNull final CropMonitoredActivity activity,
                                   @SuppressWarnings("SameParameterValue") @Nullable final String title,
                                   @NonNull final String message,
                                   @NonNull final Runnable job,
                                   @NonNull final Handler handler) {
        // Make the progress dialog not-cancelable, so that we can guarantee
        // the thread will be done before the activity getting destroyed.
        @Deprecated
        ProgressDialog progressDialog = ProgressDialog.show(activity,
                                                            title, message, true, false);
        new Thread(new BackgroundJob(activity, job, progressDialog, handler)).start();
    }

    private static class BackgroundJob
            extends
            CropMonitoredActivity.LifeCycleAdapter
            implements Runnable {

        @NonNull
        private final CropMonitoredActivity mActivity;
        @NonNull
        private final ProgressDialog mDialog;
        @NonNull
        private final Runnable mJob;
        @NonNull
        private final Handler mHandler;
        @NonNull
        private final Runnable mCleanupRunner = new Runnable() {
            public void run() {
                mActivity.removeLifeCycleListener(BackgroundJob.this);
                if (mDialog.getWindow() != null) {
                    mDialog.dismiss();
                }
            }
        };

        BackgroundJob(@NonNull final CropMonitoredActivity activity,
                      @NonNull final Runnable job,
                      @NonNull final ProgressDialog dialog,
                      @NonNull final Handler handler) {
            mActivity = activity;
            mDialog = dialog;
            mJob = job;
            mActivity.addLifeCycleListener(this);
            mHandler = handler;
        }

        public void run() {
            try {
                mJob.run();
            } finally {
                mHandler.post(mCleanupRunner);
            }
        }

        @Override
        public void onActivityDestroyed(@NonNull final CropMonitoredActivity activity) {
            // We get here only when the onDestroyed being called before
            // the mCleanupRunner. So, run it now and remove it from the queue
            mCleanupRunner.run();
            mHandler.removeCallbacks(mCleanupRunner);
        }

        @Override
        public void onActivityStopped(@NonNull final CropMonitoredActivity activity) {
            mDialog.hide();
        }

        @Override
        public void onActivityStarted(@NonNull final CropMonitoredActivity activity) {
            mDialog.show();
        }
    }


//    /**
//     * Rotates the bitmap by the specified degree.
//     * If a new bitmap is created, the original bitmap is recycled.
//     */
//    public static Bitmap rotate(Bitmap b, int degrees) {
//        if (degrees != 0 && b != null) {
//            Matrix matrix = new Matrix();
//            matrix.setRotate(degrees, (float) b.getWidth() / 2, (float) b.getHeight() / 2);
//            try {
//                Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
//                        b.getHeight(), matrix, true);
//                if (b != b2) {
//                    b.recycle();
//                    b = b2;
//                }
//            } catch (OutOfMemoryError ex) {
//                // We have no memory to rotate. Return the original bitmap.
//            }
//        }
//        return b;
//    }

//	/**
//	 * Creates a centered bitmap of the desired size. Recycles the input.
//	 */
//	public static Bitmap extractMiniThumb(Bitmap source, int width, int height, boolean recycle) {
//		if (source == null) {
//			return null;
//		}
//
//		float SCALE;
//		if (source.getWidth() < source.getHeight()) {
//			SCALE = width / (float) source.getWidth();
//		} else {
//			SCALE = height / (float) source.getHeight();
//		}
//		Matrix matrix = new Matrix();
//		matrix.setScale(SCALE, SCALE);
//		Bitmap miniThumbnail = transform(matrix, source, width, height, false);
//
//		if (recycle && miniThumbnail != source) {
//			source.recycle();
//		}
//		return miniThumbnail;
//	}

//	/**
//	 * Create a video thumbnail for a video. May return {@code null} if the video is
//	 * corrupt.
//	 *
//	 * @param filePath
//	 */
//    public static Bitmap createVideoThumbnail(String filePath) {
//        Bitmap bitmap = null;
//        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//        try {
//            retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
//            retriever.setDataSource(filePath);
//            bitmap = retriever.captureFrame();
//        } catch (RuntimeException ignore) {
//            // Assume this is a corrupt video file.
//        } finally {
//            try {
//                retriever.release();
//            } catch (RuntimeException ignore) {
//            }
//            return bitmap;
//        }

//	public static <T> int indexOf(T[] array, T s) {
//		for (int i = 0; i < array.length; i++) {
//			if (array[i].equals(s)) {
//				return i;
//			}
//		}
//		return -1;
//	}

//    /**
//     * Make a bitmap from a given Uri.
//     */
//     private static ParcelFileDescriptor makeInputStream(Uri uri, ContentResolver cr) {
//         try {
//            return cr.openFileDescriptor(uri, "r");
//         } catch (IOException ex) {
//            return null;
//         }
//     }

//	public static synchronized OnClickListener getNullOnClickListener() {
//		if (sNullOnClickListener == null) {
//			sNullOnClickListener = new OnClickListener() {
//				public void onClick(@NonNull final View v) {
//				}
//			};
//		}
//		return sNullOnClickListener;
//	}
}

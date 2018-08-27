package com.eleybourn.bookcatalogue.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.ThumbnailCacheWriterTask;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.File;
import java.util.Hashtable;
import java.util.Map;

public class ViewUtils {
    private ViewUtils() {
    }

    /**
     * Shrinks the image in the passed file to the specified dimensions, and places the image
     * in the passed view.
     *
     * @return The bitmap, or null
     */
    public static Bitmap fetchFileIntoImageView(File file, ImageView destView,
                                                int maxWidth, int maxHeight, boolean exact) {
        // Get the file, if it exists. Otherwise set 'help' icon and exit.
        if (!file.exists()) {
            if (destView != null)
                destView.setImageResource(android.R.drawable.ic_menu_help);
            return null;
        }

        return shrinkFileIntoImageView(destView, file.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Called in the UI thread, will either use a cached cover OR start a background task to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date vs the cache date. If the
     * cached image date is < the file, it is rebuilt.
     *
     * @param destView        View to populate
     * @param maxWidth        Max width of resulting image
     * @param maxHeight       Max height of resulting image
     * @param exact           Whether to fit dimensions exactly
     * @param hash            ID of book to retrieve.
     * @param checkCache      Indicates if cache should be checked for this cover
     * @param allowBackground Indicates if request can be put in background task.
     *
     * @return Bitmap (if cached) or NULL (if done in background)
     */
    public static Bitmap fetchBookCoverIntoImageView(final ImageView destView,
                                                     int maxWidth, int maxHeight,
                                                     final boolean exact, final String hash,
                                                     final boolean checkCache, final boolean allowBackground) {

        //* Get the original file so we can use the modification date, path etc */
        File coverFile = CatalogueDBAdapter.fetchThumbnailByUuid(hash);

        Bitmap bm = null;
        boolean cacheWasChecked = false;

        /* If we want to check the cache, AND we don't have cache building happening, then check it. */
        if (checkCache && !GetThumbnailTask.hasActiveTasks() && !ThumbnailCacheWriterTask.hasActiveTasks()) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance()) {
                bm = coversDbHelper.fetchCachedImageIntoImageView(coverFile, destView, hash, maxWidth, maxHeight);
            }
            cacheWasChecked = true;
        } //else {
            //System.out.println("Skipping cache check");
        //}

        if (bm != null)
            return bm;

        // Check the file exists. Otherwise set 'help' icon and exit.
        //if (!coverFile.exists()) {
        //	if (destView != null)
        //		destView.setImageResource(android.R.drawable.ic_menu_help);
        //	return null;
        //}

        // If we get here, the image is not in the cache but the original exists. See if we can queue it.
        if (allowBackground) {
            destView.setImageBitmap(null);
            GetThumbnailTask.getThumbnail(hash, destView, maxWidth, maxHeight, cacheWasChecked);
            return null;
        }

        //File coverFile = CatalogueDBAdapter.fetchThumbnail(bookId);

        // File is not in cache, original exists, we are in the background task (or not allowed to queue request)
        return shrinkFileIntoImageView(destView, coverFile.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Shrinks the passed image file spec into the specified dimensions, and returns the bitmap. If the view
     * is non-null, the image is also placed in the view.
     */
    private static Bitmap shrinkFileIntoImageView(ImageView destView, String filename, int maxWidth, int maxHeight, boolean exact) {
        Bitmap bm;

        // Read the file to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        if (new File(filename).exists()) {
            BitmapFactory.decodeFile(filename, opt);
        }

        // If no size info, or a single pixel, assume file bad and set the 'alert' icon
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            if (destView != null)
                destView.setImageResource(android.R.drawable.ic_dialog_alert);
            return null;
        }

        // Next time we don't just want the bounds, we want the file
        opt.inJustDecodeBounds = false;

        // Work out how to scale the file to fit in required bbox
        float widthRatio = (float) maxWidth / opt.outWidth;
        float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out scale so that it fit exactly
        float ratio = widthRatio < heightRatio ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        int idealSampleSize = (int) Math.ceil(1 / ratio); // This is the sample size we want to use
        // Get the nearest *bigger* power of 2.
        int samplePow2 = (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        try {
            if (exact) {
                // Create one bigger than needed and scale it; this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                Bitmap tmpBm = BitmapFactory.decodeFile(filename, opt);

                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC(), or something. Otherwise, covers in cover browser wil stay blank.
                    Logger.logError(new RuntimeException("Unexpectedly failed to decode bitmap; memory exhausted?"));
                    return null;
                }
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                // Fixup ratio based on new sample size and scale it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (bm != tmpBm) {
                    tmpBm.recycle();
                }
            } else {
                // Use a scale that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(filename, opt);
            }
        } catch (OutOfMemoryError e) {
            return null;
        }

        // Set ImageView and return bitmap
        if (destView != null) {
            destView.setImageBitmap(bm);
        }

        return bm;
    }

    /**
     * Ensure that next up/down/left/right View is visible for all sub-views of the passed view.
     */
    public static void fixFocusSettings(View root) {
        final INextView getDown = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusDownId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusDownId(id);
            }
        };
        final INextView getUp = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusUpId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusUpId(id);
            }
        };
        final INextView getLeft = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusLeftId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusLeftId(id);
            }
        };
        final INextView getRight = new INextView() {
            @Override
            public int getNext(View v) {
                return v.getNextFocusRightId();
            }

            @Override
            public void setNext(View v, int id) {
                v.setNextFocusRightId(id);
            }
        };

        Hashtable<Integer, View> vh = getViews(root);

        for (Map.Entry<Integer, View> ve : vh.entrySet()) {
            final View v = ve.getValue();
            if (v.getVisibility() == View.VISIBLE) {
                fixNextView(vh, v, getDown);
                fixNextView(vh, v, getUp);
                fixNextView(vh, v, getLeft);
                fixNextView(vh, v, getRight);
            }
        }
    }

    /**
     * Passed a collection of views, a specific View and an INextView, ensure that the
     * currently set 'next' view is actually a visible view, updating it if necessary.
     *
     * @param vh     Collection of all views
     * @param v      View to check
     * @param getter Methods to get/set 'next' view
     */
    private static void fixNextView(Hashtable<Integer, View> vh, View v, INextView getter) {
        int nextId = getter.getNext(v);
        if (nextId != View.NO_ID) {
            int actualNextId = getNextView(vh, nextId, getter);
            if (actualNextId != nextId)
                getter.setNext(v, actualNextId);
        }
    }

    /**
     * Passed a collection of views, a specific view and an INextView object find the
     * first VISIBLE object returned by INextView when called recursively.
     *
     * @param vh     Collection of all views
     * @param nextId ID of 'next' view to get
     * @param getter Interface to lookup 'next' ID given a view
     *
     * @return ID if first visible 'next' view
     */
    private static int getNextView(Hashtable<Integer, View> vh, int nextId, INextView getter) {
        final View v = vh.get(nextId);
        if (v == null)
            return View.NO_ID;

        if (v.getVisibility() == View.VISIBLE)
            return nextId;

        return getNextView(vh, getter.getNext(v), getter);
    }

    /**
     * Passed a parent View return a collection of all child views that have IDs.
     *
     * @param v Parent View
     *
     * @return Hashtable of descendants with ID != NO_ID
     */
    private static Hashtable<Integer, View> getViews(View v) {
        Hashtable<Integer, View> vh = new Hashtable<>();
        getViews(v, vh);
        return vh;
    }

    /**
     * Passed a parent view, add it and all children view (if any) to the passed collection
     *
     * @param p  Parent View
     * @param vh Collection
     */
    private static void getViews(View p, Hashtable<Integer, View> vh) {
        // Get the view ID and add it to collection if not already present.
        final int id = p.getId();
        if (id != View.NO_ID && !vh.containsKey(id)) {
            vh.put(id, p);
        }
        // If it's a ViewGroup, then process children recursively.
        if (p instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) p;
            final int nChildren = g.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                getViews(g.getChildAt(i), vh);
            }
        }
    }

    private interface INextView {
        int getNext(View v);

        void setNext(View v, int id);
    }

    /*
     * Debug utility to dump an entire view hierarchy to the output.
     *
     * @param depth
     * @param v
     */
    //public static void dumpViewTree(int depth, View v) {
    //	for(int i = 0; i < depth*4; i++)
    //		System.out.print(" ");
    //	System.out.print(v.getClass().getName() + " (" + v.getId() + ")" + (v.getId() == R.id.descriptionLabelzzz? "DESC! ->" : " ->"));
    //	if (v instanceof TextView) {
    //		String s = ((TextView)v).getText().toString();
    //		System.out.println(s.substring(0, Math.min(s.length(), 20)));
    //	} else {
    //		System.out.println();
    //	}
    //	if (v instanceof ViewGroup) {
    //		ViewGroup g = (ViewGroup)v;
    //		for(int i = 0; i < g.getChildCount(); i++) {
    //			dumpViewTree(depth+1, g.getChildAt(i));
    //		}
    //	}
    //}
}

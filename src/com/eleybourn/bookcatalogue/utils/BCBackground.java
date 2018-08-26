package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.app.ListActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;

public class BCBackground {
    private BCBackground() {
    }

    /** FIXME: init which is not functioning yet, done this way for easy future debug  */
    private static final boolean BACKGROUND_ALLOWED = false;

    /**
     * Call setCacheColorHint on a listview and trap IndexOutOfBoundsException.
     * FIXME: test in android 5 and remove
     * There is a bug in Android 2.2-2.3 (approx) that causes this call to throw
     * exceptions *sometimes* (circumstances unclear):
     *
     *     http://code.google.com/p/android/issues/detail?id=9775
     *
     * Ideally this code should use reflection to set it, or check android versions.
     *
     * @param lv		ListView to set
     * @param hint		Colour hint
     */
    private static void setCacheColorHintSafely(ListView lv, int hint) {
        try {
            lv.setCacheColorHint(hint);
        } catch (IndexOutOfBoundsException e) {
            Logger.logError("Android Bug avoided");
        }
    }
    /*
     * Set the background based on user preferences
     */
    public static void init(Activity a) {
        if (BuildConfig.DEBUG) {
            System.out.println("BCBackground.init(Activity)");
        }
        privateInit(a.findViewById(R.id.root),false);
    }

    public static void init(ListActivity a) {
        if (BuildConfig.DEBUG) {
            System.out.println("BCBackground.init(ListActivity)");
        }
        privateInit(a.findViewById(R.id.root),false);
    }

    public static void init(Fragment f) {
        if (BuildConfig.DEBUG) {
            System.out.println("BCBackground.init(Fragment)");
        }
        privateInit(f.getActivity().findViewById(R.id.root), false);
    }

    public static void init(Activity a, boolean bright) {
        if (BuildConfig.DEBUG) {
            System.out.println("BCBackground.init(Activity)");
        }
        privateInit(a.findViewById(R.id.root),bright);
    }

    //FIXME
    private static void privateInit(View root, boolean bright) {
        if (BACKGROUND_ALLOWED) {
            try {
                if (BookCatalogueApp.getPrefs().getDisableBackgroundImage()) {
                    final int backgroundColor = BookCatalogueApp.getBackgroundColor();
                    if (BuildConfig.DEBUG) {
                        System.out.println("init: 0x" + Integer.toHexString(backgroundColor));
                    }
                    root.setBackgroundColor(backgroundColor);
                    if (root instanceof ListView) {
                        setCacheColorHintSafely((ListView)root, backgroundColor);
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        System.out.println("init: drawable");
                    }
                    if (root instanceof ListView) {
                        ListView lv = ((ListView)root);
                        setCacheColorHintSafely(lv, Color.TRANSPARENT);
                    }
                    root.setBackground(makeTiledBackground(bright));
                }
                root.invalidate();
            } catch (Exception e) {
                // Usually the errors result from memory problems; do a gc just in case.
                System.gc();
                // This is a purely cosmetic function; just log the error
                Logger.logError(e, "Error setting background");
            }
        }
    }

    // FIXME
    public static void init(View root, ListView lv, View header) {
        if (BACKGROUND_ALLOWED) {
            Drawable d = null;
            try {
                if (BookCatalogueApp.getPrefs().getDisableBackgroundImage()) {
                    d = makeTiledBackground(false);
                }
            } catch (Exception e) {
                // Ignore...if just a coat of paint
            }

            if (BooklistPreferencesActivity.isBackgroundFlat() || d == null) {
                final int backgroundColor = BookCatalogueApp.getBackgroundColor();
                lv.setBackgroundColor(backgroundColor);
                setCacheColorHintSafely(lv, backgroundColor);
                if (d == null) {
                    root.setBackgroundColor(backgroundColor);
                    header.setBackgroundColor(backgroundColor);
                } else {
                    root.setBackground(d);
                    header.setBackground(d);
                }
            } else {
                setCacheColorHintSafely(lv, Color.TRANSPARENT);
                // ICS does not cope well with transparent ListView backgrounds with a 0 cache hint, but it does
                // seem to cope with a background image on the ListView itself.
                root.setBackground(d);
//				root.setBackgroundDrawable(Utils.cleanupTiledBackground(getResources().getDrawable(R.drawable.bc_background_gradient_dim)));
                header.setBackgroundColor(Color.TRANSPARENT);
            }
            root.invalidate();
        }
    }

    /**
     * Reuse of bitmaps in tiled backgrounds is a known cause of problems:
     *		http://stackoverflow.com/questions/4077487/background-image-not-repeating-in-android-layout
     * So we avoid reusing them.
     *
     * This seems to have become further messed up in 4.1 so now, we just created them manually. No references,
     * but the old cleanup method (see below for cleanupTiledBackground()) no longer works. Since it effectively
     * un-cached the background, we just create it here.
     *
     * The main problem with this approach is that the style is defined in code rather than XML.
     *
     * @param bright	Flag indicating if background should be 'bright'
     *
     * @return			Background Drawable
     */
    private static Drawable makeTiledBackground(boolean bright) {
        // Storage for the layers
        Drawable[] drawables = new Drawable[2];
        // Get the BG image, put in tiled drawable
        Bitmap b = BitmapFactory.decodeResource(BookCatalogueApp.getAppContext().getResources(), R.drawable.books_bg);
        BitmapDrawable bmD = new BitmapDrawable(BookCatalogueApp.getAppContext().getResources(), b);
        bmD.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        // Add to layers
        drawables[0] = bmD;

        // Set up the gradient colours based on 'bright' setting
        int[] colours = new int[3];
        if (bright) {
            colours[0] = Color.argb(224, 0, 0, 0);
            colours[1] = Color.argb(208, 0, 0, 0);
            colours[2] = Color.argb(48, 0, 0, 0);
        } else {
            colours[0] = Color.argb(255, 0, 0, 0);
            colours[1] = Color.argb(208, 0, 0, 0);
            colours[2] = Color.argb(160, 0, 0, 0);
        }

        // Create a gradient and add to layers
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colours);
        drawables[1] = gd;

        // Make the layers and we are done.
        LayerDrawable ll = new LayerDrawable(drawables);
        ll.setDither(true);

        return ll;
    }

    ///**
    // * Reuse of bitmaps in tiled backgrounds is a known cause of problems:
    // *		http://stackoverflow.com/questions/4077487/background-image-not-repeating-in-android-layout
    // * So we avoid reusing them
    // *
    // * @param d		Drawable background that may be a BitmapDrawable or a layered drawablewhose first
    // * 				layer is a tiled bitmap
    // *
    // * @return		Modified Drawable
    // */
    //private static Drawable cleanupTiledBackground(Drawable d) {
    //	if (d instanceof LayerDrawable) {
    //		System.out.println("BG: BG is layered");
    //		LayerDrawable ld = (LayerDrawable)d;
    //		Drawable l = ld.getDrawable(0);
    //		if (l instanceof BitmapDrawable) {
    //			d.mutate();
    //			l.mutate();
    //			System.out.println("BG: Layer0 is BMP");
    //			BitmapDrawable bmp = (BitmapDrawable) l;
    //			bmp.mutate(); // make sure that we aren't sharing state anymore
    //			//bmp.setTileModeXY(TileMode.CLAMP, TileMode.CLAMP);
    //			bmp.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
    //		} else {
    //			System.out.println("BG: Layer0 is " + l.getClass().getSimpleName() + " (ignored)");
    //		}
    //	} else if (d instanceof BitmapDrawable) {
    //		System.out.println("BG: Drawable is BMP");
    //		BitmapDrawable bmp = (BitmapDrawable) d;
    //		bmp.mutate(); // make sure that we aren't sharing state anymore
    //		//bmp.setTileModeXY(TileMode.CLAMP, TileMode.CLAMP);
    //		bmp.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
    //	}
    //	return d;
    //}

}

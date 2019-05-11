package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * Interface defining required methods for any external scanner interface.
 *
 * @author pjw
 */
public interface Scanner {

    /**
     * Start the activity with the passed request code.
     *
     * @param activity    calling activity
     * @param requestCode which will be passed back to onActivityResult
     */
    void startActivityForResult(@NonNull Activity activity,
                                int requestCode);

    /**
     * @return the barcode from the resulting intent of a scan action.
     */
    @NonNull
    String getBarcode(@NonNull Intent data);
}

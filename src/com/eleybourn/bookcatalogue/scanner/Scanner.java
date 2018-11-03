package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.UniqueId;

/**
 * Interface defining required methods for any external scanner interface.
 *
 * @author pjw
 */
public interface Scanner {
    int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_SCANNER;

    /** tag used in {@link Intent#getStringExtra} */
    String SCAN_RESULT = "SCAN_RESULT";

    /** Request a scan */
    void startActivityForResult(final @NonNull Activity activity, final int requestCode);

    /** Get the barcode from the resulting intent */
    @NonNull
    String getBarcode(final @NonNull Intent intent);
}

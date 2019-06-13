package com.eleybourn.bookcatalogue.dialogs;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

/**
 * Wrapper for the zoomed image dialog.
 */
public class ZoomedImageDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = "ZoomedImageDialogFragment";

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final File imageFile) {
        // if there is no file, just silently ignore.
        if (imageFile.exists()) {
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(imageFile).show(fm, TAG);
            }
        }
    }

    /**
     * Constructor.
     *
     * @param image to display
     *
     * @return the instance
     */
    private static ZoomedImageDialogFragment newInstance(@NonNull final File image) {
        ZoomedImageDialogFragment frag = new ZoomedImageDialogFragment();
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_FILE_SPEC, image.getPath());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.dialog_zoomed_image, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {

        Bundle args = requireArguments();

        File imageFile = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean upscale = App.getPrefs().getBoolean(Prefs.pk_thumbnails_zoom_upscale, true);

        ImageView imageView = view.findViewById(R.id.coverImage);
        ImageUtils.setImageView(imageView, imageFile,
                                metrics.widthPixels,
                                metrics.heightPixels,
                                upscale);

        imageView.setOnClickListener(v -> dismiss());
    }
}

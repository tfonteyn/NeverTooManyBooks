package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Implementation of {@link FileDetails} that collects data about backup files
 * in a background thread.
 *
 * @author pjw
 */
public class BackupFileDetails
    implements FileDetails, Parcelable {

    public static final Parcelable.Creator<BackupFileDetails> CREATOR =
        new Parcelable.Creator<BackupFileDetails>() {
            public BackupFileDetails createFromParcel(@NonNull final Parcel source) {
                return new BackupFileDetails(source);
            }

            public BackupFileDetails[] newArray(final int size) {
                return new BackupFileDetails[size];
            }
        };

    /** @see #isArchive(File) */
    public static final String ARCHIVE_EXTENSION = ".bcbk";
    public static final String ARCHIVE_PREFIX = "BookCatalogue-";

    /** File for this item */
    @NonNull
    private final File mFile;
    /** The BackupInfo we use when displaying the object */
    @Nullable
    private BackupInfo mInfo;

    /**
     * Constructor
     */
    BackupFileDetails(@NonNull final File file) {
        mFile = file;
    }

    /**
     * Constructor, using a Parcel as source.
     */
    private BackupFileDetails(@NonNull final Parcel in) {
        mFile = new File(in.readString());
        // flag to indicate the Parcel has the info bundle
        boolean hasInfo = (in.readByte() != 0);
        if (hasInfo) {
            mInfo = in.readParcelable(getClass().getClassLoader());
        } else {
            mInfo = null;
        }
    }

    public static boolean isArchive(@NonNull final File f) {
        return f.getName().toLowerCase().endsWith(ARCHIVE_EXTENSION);
    }

    public void setInfo(@NonNull final BackupInfo info) {
        mInfo = info;
    }

    @NonNull
    @Override
    public File getFile() {
        return mFile;
    }

    /**
     * Return the view we use.
     *
     * THIS SHOULD ALWAYS RETURN THE SAME VIEW. IT IS NOT A MULTI-TYPE LIST.
     */
    @Override
    public int getViewId() {
        return R.layout.row_backup_chooser_item;
    }

    /**
     * Fill in the details for the view we returned above.
     */
    @Override
    public void onGetView(@NonNull final View convertView,
                          @NonNull final Context context) {

        // Set the basic data
        TextView filenameView = convertView.findViewById(R.id.name);
        filenameView.setText(mFile.getName());

        ImageView imageView = convertView.findViewById(R.id.icon);
        TextView detailsView = convertView.findViewById(R.id.details);
        TextView dateView = convertView.findViewById(R.id.date);
        TextView sizeView = convertView.findViewById(R.id.size);

        // For directories, hide the extra data
        if (mFile.isDirectory()) {
            dateView.setVisibility(View.GONE);
            detailsView.setVisibility(View.GONE);
            imageView.setImageDrawable(context.getDrawable(R.drawable.ic_folder));
        } else {
            // Display date and backup details
            imageView.setImageDrawable(context.getDrawable(R.drawable.bc_archive));
            dateView.setVisibility(View.VISIBLE);
            String formattedFileSize = Utils.formatFileSize(mFile.length());
            Resources res = context.getResources();
            if (mInfo != null) {
                List<String> args = new ArrayList<>();
                if (mInfo.hasBookCount()) {
                    args.add(res.getQuantityString(R.plurals.n_books, mInfo.getBookCount(),
                                                   mInfo.getBookCount()));
                } else if (mInfo.hasBooks()) {
                    args.add(res.getString(R.string.lbl_books));
                }
                if (mInfo.hasCoverCount()) {
                    args.add(res.getQuantityString(R.plurals.n_covers, mInfo.getCoverCount(),
                                                   mInfo.getCoverCount()));
                } else if (mInfo.hasCovers()) {
                    args.add(res.getString(R.string.lbl_covers));
                }

                if (mInfo.hasPreferences()) {
                    args.add(res.getString(R.string.lbl_preferences));
                }
                if (mInfo.hasBooklistStyles()) {
                    args.add(res.getString(R.string.lbl_styles));
                }

                // needs RTL
                detailsView.setText(TextUtils.join(", ", args));

                sizeView.setText(formattedFileSize);
                dateView.setText(DateUtils.toPrettyDateTime(mInfo.getCreationDate()));
                detailsView.setVisibility(View.VISIBLE);
            } else {
                sizeView.setText(formattedFileSize);
                dateView.setText(DateUtils.toPrettyDateTime(new Date(mFile.lastModified())));
                detailsView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * {@link Parcelable} INTERFACE.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Save all fields that must be persisted.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mFile.getAbsolutePath());
        if (mInfo != null) {
            // flag to indicate the Parcel has the info bundle
            dest.writeByte((byte) 1);
            dest.writeParcelable(mInfo, flags);
        } else {
            dest.writeByte((byte) 0);
        }
    }

}
package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Present a list of files for selection.
 */
public class FilePicker
        extends ValuePicker {

    /**
     * Constructor.
     *
     * @param context caller context
     * @param title   for the dialog
     * @param message optional message
     * @param files   list to choose from
     * @param handler which will receive the selected row item
     */
    public FilePicker(@NonNull final Context context,
                      @Nullable final String title,
                      @Nullable final String message,
                      @NonNull final List<File> files,
                      @NonNull final OnPickListener<File> handler) {
        super(context, title, message);

        final FileItemListAdapter adapter = new FileItemListAdapter(context, files, (item) -> {
            dismiss();
            handler.onPicked(item);
        });
        setAdapter(adapter, 0);
    }

    private static class FileItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        final Locale mLocale;
        @NonNull
        private final List<File> mList;
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final OnPickListener<File> mListener;

        FileItemListAdapter(@NonNull final Context context,
                            @NonNull final List<File> objects,
                            @NonNull final OnPickListener<File> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mLocale = LocaleUtils.from(context);
            mList = objects;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View root = mInflater.inflate(R.layout.row_file_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Context context = mInflater.getContext();

            File item = mList.get(position);
            holder.name.setText(item.getName());
            holder.path.setText(item.getParent());
            holder.size.setText(Utils.formatFileSize(context, item.length()));
            holder.lastModDate.setText(DateUtils.toPrettyDateTime(mLocale,
                                                                  new Date(item.lastModified())));

            // onClick on the whole view.
            holder.itemView.setOnClickListener((v) -> mListener.onPicked(item));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView name;
        @NonNull
        final TextView path;
        @NonNull
        final TextView size;
        @NonNull
        final TextView lastModDate;


        Holder(@NonNull final View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.name);
            path = itemView.findViewById(R.id.path);
            size = itemView.findViewById(R.id.size);
            lastModDate = itemView.findViewById(R.id.date);
        }
    }
}

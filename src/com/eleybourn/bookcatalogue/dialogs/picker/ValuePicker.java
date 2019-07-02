package com.eleybourn.bookcatalogue.dialogs.picker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

/**
 * Provides an AlertDialog with an optional title and message.
 * The content is a list of options, behaving like a menu.
 * <p>
 * So you basically get a 'deluxe' {@link PopupMenu}.
 */
public class ValuePicker {

    @NonNull
    private final RecyclerView mListView;
    @NonNull
    private final AlertDialog mDialog;

    ValuePicker(@NonNull final Context context,
                @Nullable final String title,
                @Nullable final String message) {

        // Build the base dialog
        final View root = LayoutInflater.from(context).inflate(R.layout.dialog_popupmenu, null);

        // Optional message
        TextView messageView = root.findViewById(R.id.message);
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
            messageView.setVisibility(View.VISIBLE);
        } else {
            messageView.setVisibility(View.GONE);
        }

        // list of options
        mListView = root.findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        mListView.setLayoutManager(linearLayoutManager);
//        mListView.addItemDecoration(
//                new DividerItemDecoration(context, linearLayoutManager.getOrientation()));

        // there is no 'positive' button. The user chooses one of the list options instead.
        mDialog = new AlertDialog.Builder(context)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .create();

        // optional title
        if (title != null && !title.isEmpty()) {
            mDialog.setTitle(title);
        }
    }

    public void show() {
        mDialog.show();
    }

    void dismiss() {
        mDialog.dismiss();
    }

    /**
     * @param adapter          to use
     * @param scrollToPosition position to scroll initially to. Set to 0 for no scroll.
     */
    void setAdapter(@NonNull final RecyclerView.Adapter adapter,
                    final int scrollToPosition) {
        mListView.setAdapter(adapter);
        mListView.scrollToPosition(scrollToPosition);
    }

    void setTitle(@NonNull final CharSequence title) {
        mDialog.setTitle(title);
    }

    /**
     * Interface to listen for item selection in a custom dialog list.
     */
    public interface PickListener<T> {

        void onPicked(@NonNull T item);
    }
}

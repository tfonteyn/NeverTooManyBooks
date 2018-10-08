/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsRegister;
import com.eleybourn.bookcatalogue.searches.librarything.AdministrationLibraryThing;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StandardDialogs {
    //FIXME: make this a preference, or dump toast ?
    private static final boolean USE_SNACKBAR = true;

    private static final String UNKNOWN = "<" + BookCatalogueApp.getResourceString(R.string.unknown_uc) + ">";

    public static void showQuickNotice(@NonNull final Activity activity, @StringRes final int message) {
        if (USE_SNACKBAR) {
            Snackbar.make(activity.getWindow().getDecorView(), message, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    public static void showQuickNotice(@NonNull final Activity activity, @NonNull final String message) {
        if (USE_SNACKBAR) {
            Snackbar.make(activity.getWindow().getDecorView(), message, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show a dialog asking if unsaved edits should be ignored. Finish if so.
     */
    public static void showConfirmUnsavedEditsDialog(@NonNull final Activity activity, @Nullable final Runnable r) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.details_have_changed)
                .setMessage(R.string.you_have_unsaved_changes)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.exit),
                new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        if (r != null) {
                            r.run();
                        } else {
                            activity.finish();
                        }
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.continue_editing),
                new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    public static void needLibraryThingAlert(@NonNull final Context context,
                                             final boolean ltRequired,
                                             @NonNull final String prefSuffix) {
        boolean showAlert;
        int msgId;
        final String prefName = LibraryThingManager.PREFS_LT_HIDE_ALERT + "_" + prefSuffix;
        if (!ltRequired) {
            msgId = R.string.uses_library_thing_info;
            SharedPreferences prefs = context.getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, android.content.Context.MODE_PRIVATE);
            showAlert = !prefs.getBoolean(prefName, false);
        } else {
            msgId = R.string.require_library_thing_info;
            showAlert = true;
        }

        if (!showAlert)
            return;

        final AlertDialog dialog = new AlertDialog.Builder(context).setMessage(msgId)
                .setTitle(R.string.reg_library_thing_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.more_info),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        Intent i = new Intent(context, AdministrationLibraryThing.class);
                        context.startActivity(i);
                        dialog.dismiss();
                    }
                });

        if (!ltRequired) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.disable_dialogue),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            SharedPreferences prefs = context.getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
                            SharedPreferences.Editor ed = prefs.edit();
                            ed.putBoolean(prefName, true);
                            ed.apply();
                            dialog.dismiss();
                        }
                    });
        }

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    public static void deleteSeriesAlert(@NonNull final Context context,
                                         @NonNull final CatalogueDBAdapter db,
                                         @NonNull final Series series,
                                         final Runnable onDeleted) {

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(String.format(context.getString(R.string.really_delete_series), series.name))
                .setTitle(R.string.delete_series)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        db.deleteSeries(series.id);
                        dialog.dismiss();
                        onDeleted.run();
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    /**
     * @return the resource id for a string in case of error, 0 for ok
     */
    public static int deleteBookAlert(@NonNull final Context context,
                                      @NonNull final CatalogueDBAdapter db,
                                      final long id,
                                      @NonNull final Runnable onDeleted) {

        List<Author> authorList = db.getBookAuthorList(id);

        String title;
        try (Cursor cursor = db.fetchBookById(id)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return R.string.unable_to_find_book;
            }

            title = cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name));
            if (title == null || title.isEmpty()) {
                title = UNKNOWN;
            }
        }

        // Format the list of authors nicely
        StringBuilder authors = new StringBuilder();
        if (authorList.size() == 0) {
            authors.append(UNKNOWN);
        } else {
            authors.append(authorList.get(0).getDisplayName());
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getDisplayName());
            }
            if (authorList.size() > 1) {
                authors.append(" ").append(context.getString(R.string.list_and)).append(" ").append(authorList.get(authorList.size() - 1).getDisplayName());
            }
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage((context.getString(R.string.really_delete_book, title, authors)))
                .setTitle(R.string.menu_delete)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        db.deleteBook(id);
                        dialog.dismiss();
                        onDeleted.run();
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
        return 0;

    }

    /**
     * Display a dialog warning the user that goodreads authentication is required;
     * gives the options: 'request now', 'more info' or 'cancel'.
     */
    public static void goodreadsAuthAlert(@NonNull final FragmentActivity context) {
        // Get the title
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.authorize_access)
                .setMessage(R.string.goodreads_action_cannot_blah_blah)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        GoodreadsRegister.requestAuthorizationInBackground(context);
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.tell_me_more),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        Intent i = new Intent(context, GoodreadsRegister.class);
                        context.startActivity(i);
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                });

        dialog.show();

    }

    /**
     * Select a custom item from a list, and call handler when/if item is selected.
     */
    public static void selectItemDialog(@NonNull final LayoutInflater inflater,
                                        @Nullable final String message,
                                        @NonNull final List<SimpleDialogItem> items,
                                        @Nullable final SimpleDialogItem selectedItem,
                                        @NonNull final SimpleDialogOnClickListener handler) {
        // Get the view and the radio group
        final View root = inflater.inflate(R.layout.dialog_select_from_list, null);
        TextView msg = root.findViewById(R.id.message);

        // Build the base dialog and the top message (if any)
        final AlertDialog.Builder builder = new AlertDialog.Builder(inflater.getContext())
                .setView(root);
        if (message != null && !message.isEmpty()) {
            msg.setText(message);
        } else {
            msg.setVisibility(View.GONE);
            root.findViewById(R.id.messageBottomDivider).setVisibility(View.GONE);
        }

        final AlertDialog dialog = builder.create();

        // Create the listener for each item
        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(final View v) {
                SimpleDialogItem item = ViewTagger.getTag(v, R.id.TAG_DIALOG_ITEM);
                // For a consistent UI, make sure the selector is checked as well. NOT mandatory from
                // a functional point of view, just consistent
                if (item != null && !(v instanceof Checkable)) {
                    RadioButton btn = item.getSelector(v);
                    if (btn != null) {
                        btn.setChecked(true);
                        btn.invalidate();
                    }
                }
                //
                // It would be nice to have the other radio buttons reflect the new state before it
                // disappears, but not really worth the effort. Esp. since the code below does not work...
                // and the dialog disappears too fast to make this worthwhile.
                //
                //LinearLayout list = (LinearLayout)root.findViewById(R.id.list);
                //for(int i = 0; i < list.getChildCount(); i++) {
                //	View child = list.getChildAt(i);
                //	SimpleDialogItem other = (SimpleDialogItem)ViewTagger.getTag(child, R.id.TAG_DIALOG_ITEM);
                //	RadioButton btn = other.getSelector(child);
                //	btn.setSelected(other == item);
                //	btn.invalidate();
                //}
                dialog.dismiss();
                if (item != null) {
                    handler.onClick(item);
                }
            }
        };

        // Add the items to the dialog
        LinearLayout list = root.findViewById(android.R.id.list);
        for (SimpleDialogItem item : items) {
            View view = item.getView(inflater);
            view.setOnClickListener(listener);
            view.setBackgroundResource(android.R.drawable.list_selector_background);

            ViewTagger.setTag(view, R.id.TAG_DIALOG_ITEM, item);
            list.addView(view);

            RadioButton btn = item.getSelector(view);
            if (btn != null) {
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                btn.setChecked(item == selectedItem);
                btn.setOnClickListener(listener);
            }
        }
        dialog.show();
    }

    /**
     * Wrapper class to present a list of files for selection
     */
    public static void selectFileDialog(@NonNull final LayoutInflater inflater,
                                        @Nullable final String title,
                                        @NonNull final List<File> files,
                                        @NonNull final SimpleDialogOnClickListener handler) {
        List<SimpleDialogItem> items = new ArrayList<>();
        for (File file : files) {
            items.add(new SimpleDialogFileItem(file));
        }
        selectItemDialog(inflater, title, items, null, handler);
    }

    /**
     * Wrapper class to present a list of arbitrary objects for selection; it uses
     * the toString() method to display a simple list.
     */
    public static <T> void selectStringDialog(@NonNull final LayoutInflater inflater,
                                              @Nullable final String title,
                                              @NonNull final List<T> objects,
                                              @Nullable final String current,
                                              @NonNull final SimpleDialogOnClickListener handler) {
        List<SimpleDialogItem> items = new ArrayList<>();
        SimpleDialogItem selectedItem = null;
        for (T object : objects) {
            SimpleDialogObjectItem item = new SimpleDialogObjectItem(object);
            if (current != null && object.toString().equalsIgnoreCase(current))
                selectedItem = item;
            items.add(item);
        }
        selectItemDialog(inflater, title, items, selectedItem, handler);
    }

    /**
     * Interface for item that displays in a custom dialog list
     */
    public interface SimpleDialogItem {
        View getView(@NonNull final LayoutInflater inflater);

        RadioButton getSelector(View v);
    }

    /**
     * Interface to listen for item selection in a custom dialog list
     */
    public interface SimpleDialogOnClickListener {
        void onClick(@NonNull final SimpleDialogItem item);
    }

    /**
     * Simple item to manage a File object in a list of items.
     */
    public static class SimpleDialogFileItem implements SimpleDialogItem {
        private final File mFile;

        SimpleDialogFileItem(@NonNull final File file) {
            mFile = file;
        }

        public File getFile() {
            return mFile;
        }

        @Nullable
        public RadioButton getSelector(final View v) {
            return null;
        }

        /**
         * Get a View to display the file
         */
        public View getView(@NonNull final LayoutInflater inflater) {
            // Create the view
            View v = inflater.inflate(R.layout.dialog_file_list_item, null);
            // Set the file name
            TextView name = v.findViewById(R.id.name);
            name.setText(mFile.getName());
            // Set the path
            TextView location = v.findViewById(R.id.path);
            location.setText(mFile.getParent());
            // Set the size
            TextView size = v.findViewById(R.id.size);
            size.setText(Utils.formatFileSize(mFile.length()));
            // Set the last modified date
            TextView update = v.findViewById(R.id.updated);
            update.setText(DateUtils.toPrettyDateTime(new Date(mFile.lastModified())));
            // Return it
            return v;
        }
    }

    /**
     * Item to manage an Object in a list of items.
     */
    public static class SimpleDialogObjectItem implements SimpleDialogItem {
        private final Object mObject;

        SimpleDialogObjectItem(@NonNull final Object object) {
            mObject = object;
        }

        /**
         * Get a View to display the object -> toString() and put into RadioButton.text
         */
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater) {
            @SuppressLint("InflateParams") // root==null as it's a dialog
            View view = inflater.inflate(R.layout.dialog_string_list_item, null);
            TextView name = view.findViewById(R.id.name);
            name.setText(mObject.toString());
            return view;
        }

        @NonNull
        public RadioButton getSelector(final View view) {
            return (RadioButton) view.findViewById(R.id.selector);
        }

        /**
         * Get the underlying object as a string
         */
        @Override
        public String toString() {
            return mObject.toString();
        }
    }

    public static class SimpleDialogMenuItem extends SimpleDialogObjectItem {
        final int mItemId;
        final int mDrawableId;

        public SimpleDialogMenuItem(@NonNull final Object object, final int itemId, @DrawableRes final int icon) {
            super(object);
            mItemId = itemId;
            mDrawableId = icon;
        }

        public int getItemId() {
            return mItemId;
        }

        @Override
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater) {
            View view = super.getView(inflater);
            TextView name = view.findViewById(R.id.name);
            name.setCompoundDrawablesWithIntrinsicBounds(mDrawableId, 0, 0, 0);
            // Now make the actual RadioButton gone
            getSelector(view).setVisibility(View.GONE);
            return view;
        }
    }

    /**
     * This class exists only for:
     * - we use AppCompatDialog in ONE place (here) .. so any future removal is easy
     * - optional Close Button, if your layout has a Button with id=android.R.id.closeButton
     */
    public static class BasicDialog extends AppCompatDialog {

        public BasicDialog(@NonNull final Context context) {
            this(context, BookCatalogueApp.getDialogThemeResId());
        }
        public BasicDialog(@NonNull final Context context, @StyleRes final int theme) {
            super(context, theme);

            Button closeButton = findViewById(android.R.id.closeButton);
            if (closeButton != null) {
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BasicDialog.this.dismiss();
                    }
                });
            }
        }
    }
}

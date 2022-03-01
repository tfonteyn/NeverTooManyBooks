/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.debug;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSqliteShellBinding;

/**
 * A crude sql shell.
 */
public class SqliteShellFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SqliteShellFragment";

    private static final String BKEY_ALLOW_UPDATES = TAG + ":upd";

    private static final int MAX_LINES = 200;
    private static final String UTF_8 = "utf-8";
    private static final String TEXT_HTML = "text/html";
    private static final String STR_LAST_COUNT = "Rows: ";
    private static final String STR_ROWS_AFFECTED = "Rows affected: ";
    private static final String STR_UPDATES_NOT_ALLOWED = "Updates are not allowed";

    private static final String SQL_LIST_TABLES =
            "SELECT '' AS T, tbl_name FROM sqlite_master WHERE type='table'"
            + " UNION "
            + "SELECT 'T' AS T, tbl_name FROM sqlite_temp_master WHERE type='table'"
            + " ORDER BY tbl_name";

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;
    private boolean mAllowUpdates;
    /** View Binding. */
    private FragmentSqliteShellBinding mVb;
    private SynchronizedDb mDb;

    @NonNull
    public static Fragment create(final boolean allowUpdates) {
        final Fragment fragment = new SqliteShellFragment();
        final Bundle args = new Bundle(1);
        args.putBoolean(BKEY_ALLOW_UPDATES, allowUpdates);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            mAllowUpdates = args.getBoolean(BKEY_ALLOW_UPDATES);
        }

        mDb = ServiceLocator.getInstance().getDb();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentSqliteShellBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());

        final WebSettings settings = mVb.output.getSettings();
        settings.setTextZoom(75);
    }

//    private void textSmaller() {
//        final WebSettings settings = outputView.getSettings();
//        settings.setTextZoom(settings.getTextZoom() - 10);
//    }
//
//    private void textBigger() {
//        final WebSettings settings = outputView.getSettings();
//        settings.setTextZoom(settings.getTextZoom() + 10);
//    }

    private void executeSql(@NonNull final String sql) {
        final String lcSql = sql.toLowerCase(Locale.ROOT);
        try {
            if (lcSql.startsWith("update") || lcSql.startsWith("delete")) {
                getToolbar().setTitle("");

                if (mAllowUpdates) {
                    try (SynchronizedStatement stmt = mDb.compileStatement(sql)) {
                        final int rowsAffected = stmt.executeUpdateDelete();
                        final String result = STR_ROWS_AFFECTED + rowsAffected;
                        mVb.output.loadDataWithBaseURL(null, result,
                                                       TEXT_HTML, UTF_8, null);
                    }
                } else {
                    mVb.output.loadDataWithBaseURL(null, STR_UPDATES_NOT_ALLOWED,
                                                   TEXT_HTML, UTF_8, null);
                }
            } else {
                try (Cursor cursor = mDb.rawQuery(sql, null)) {
                    getToolbar().setTitle(STR_LAST_COUNT + cursor.getCount());

                    final StringBuilder sb = new StringBuilder("<table>");
                    final String[] columnNames = cursor.getColumnNames();
                    sb.append("<tr>");
                    for (final String column : columnNames) {
                        sb.append("<td><i>").append(column).append("</i></td>");
                    }
                    sb.append("</tr>");

                    int maxLines = MAX_LINES;
                    while (cursor.moveToNext() && maxLines > 0) {
                        final StringBuilder line = new StringBuilder();
                        for (int c = 0; c < cursor.getColumnCount(); c++) {
                            line.append("<td>").append(cursor.getString(c)).append("</td>");
                        }
                        sb.append("<tr>").append(line).append("</tr>");
                        maxLines--;
                    }
                    sb.append("</table>");

                    mVb.output.loadDataWithBaseURL(null, sb.toString(),
                                                   TEXT_HTML, UTF_8, null);
                }
            }
        } catch (@NonNull final Exception e) {
            getToolbar().setTitle("");

            mVb.output.loadDataWithBaseURL(null, String.valueOf(e.getMessage()),
                                           TEXT_HTML, UTF_8, null);
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_RUN, 0, R.string.debug_sq_shell_run)
                .setIcon(mAllowUpdates ? R.drawable.ic_baseline_warning_24
                                       : R.drawable.ic_baseline_directions_run_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES, 0,
                     R.string.debug_sq_shell_list_tables)
                .setIcon(R.drawable.ic_baseline_format_list_bulleted_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_DEBUG_SQ_SHELL_RUN) {
                executeSql(mVb.input.getText().toString().trim());
                return true;

            } else if (itemId == R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES) {
                executeSql(SQL_LIST_TABLES);
                return true;
            }
            return false;
        }
    }
}

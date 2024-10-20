/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSqliteShellBinding;

/**
 * A crude sql shell.
 */
public class SqliteShellFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SqliteShellFragment";

    private static final String BKEY_ALLOW_UPDATES = TAG + ":upd";

    private static final String PK_SQLITE_MAX_LINES = "sqlite.shell.max.lines";

    private static final int MAX_LINES = 200;

    private static final String UTF_8 = "utf-8";
    private static final String TEXT_HTML = "text/html";

    private static final String SQL_LIST_TABLES =
            "SELECT '' AS T, tbl_name FROM sqlite_master WHERE type='table'"
            + " UNION "
            + "SELECT 'T' AS T, tbl_name FROM sqlite_temp_master WHERE type='table'"
            + " ORDER BY tbl_name";
    private static final String TR_START = "<tr>";
    private static final String TR_END = "</tr>";
    private static final int TEXT_ZOOM = 75;

    private boolean allowUpdates;
    /** View Binding. */
    private FragmentSqliteShellBinding vb;
    private SynchronizedDb db;

    private int maxLines = MAX_LINES;

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
            allowUpdates = args.getBoolean(BKEY_ALLOW_UPDATES);
        }

        db = ServiceLocator.getInstance().getDb();
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSqliteShellBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.fragmentRootView(view);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        final WebSettings settings = vb.output.getSettings();
        settings.setTextZoom(TEXT_ZOOM);

        vb.input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                hideKeyboard(v);
                execute();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection DataFlowIssue
        maxLines = PreferenceManager.getDefaultSharedPreferences(getContext())
                                    .getInt(PK_SQLITE_MAX_LINES, MAX_LINES);
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

    private void execute() {
        executeSql(vb.input.getText().toString().trim());
    }

    private void executeSql(@NonNull final String sql) {
        final String lcSql = sql.toLowerCase(Locale.ROOT);
        //noinspection OverlyBroadCatchBlock,CheckStyle
        try {
            if (lcSql.startsWith("update") || lcSql.startsWith("delete")) {
                getToolbar().setTitle("");

                if (allowUpdates) {
                    final int rowsAffected;
                    try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                        rowsAffected = stmt.executeUpdateDelete();
                    }
                    final String result = getString(R.string.debug_sq_shell_rows_affected_x,
                                                    rowsAffected);
                    vb.output.loadDataWithBaseURL(null, result,
                                                  TEXT_HTML, UTF_8, null);
                } else {
                    vb.output.loadDataWithBaseURL(
                            null,
                            getString(R.string.debug_sq_shell_warning_updates_not_allowed),
                            TEXT_HTML, UTF_8, null);
                }
            } else {
                //TODO: parse for keyword 'limit' and add default if not present
                try (Cursor cursor = db.rawQuery(sql, null)) {
                    final String result = getString(R.string.debug_sq_shell_rows_x,
                                                    cursor.getCount());
                    getToolbar().setTitle(result);

                    final StringBuilder sb = new StringBuilder("<table>");
                    final String[] columnNames = cursor.getColumnNames();
                    sb.append(TR_START);
                    for (final String column : columnNames) {
                        sb.append("<td><i>").append(column).append("</i></td>");
                    }
                    sb.append(TR_END);

                    int lines = this.maxLines;
                    while (cursor.moveToNext() && lines > 0) {
                        final StringBuilder line = new StringBuilder();
                        for (int c = 0; c < cursor.getColumnCount(); c++) {
                            line.append("<td>").append(cursor.getString(c)).append("</td>");
                        }
                        sb.append(TR_START).append(line).append(TR_END);
                        lines--;
                    }
                    sb.append("</table>");

                    vb.output.loadDataWithBaseURL(null, sb.toString(),
                                                  TEXT_HTML, UTF_8, null);
                }
            }
        } catch (@NonNull final Exception e) {
            getToolbar().setTitle("");

            vb.output.loadDataWithBaseURL(null, String.valueOf(e.getMessage()),
                                          TEXT_HTML, UTF_8, null);
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_RUN, 0, R.string.debug_sq_shell_run)
                .setIcon(allowUpdates ? R.drawable.directions_run_dangerously_24px
                                      : R.drawable.directions_run_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES, 0,
                     R.string.debug_sq_shell_list_tables)
                .setIcon(R.drawable.info_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_EDIT_MAX_LINES, 0,
                     R.string.lbl_settings)
                .setIcon(R.drawable.settings_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_DEBUG_SQ_SHELL_RUN) {
                execute();
                return true;

            } else if (menuItemId == R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES) {
                executeSql(SQL_LIST_TABLES);
                return true;

            } else if (menuItemId == R.id.MENU_DEBUG_SQ_SHELL_EDIT_MAX_LINES) {
                //noinspection DataFlowIssue
                final Intent intent = FragmentHostActivity
                        .createIntent(getContext(), SqlitePreferenceFragment.class);
                startActivity(intent);
                return true;
            }
            return false;
        }
    }
}

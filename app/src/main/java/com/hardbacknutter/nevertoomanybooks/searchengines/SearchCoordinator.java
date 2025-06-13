/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #activeTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#getEngineId()} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements Cancellable {

    private static final String ERROR_UNKNOWN_TASK = "Unknown task=";
    private static final String ERROR_UNKNOWN_SEARCH = "Unknown search=";

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";
    /** The data returned from the search can contain this key with error messages. */
    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    protected final MutableLiveData<LiveDataEvent<TaskProgress>>
            searchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorFinished = new MutableLiveData<>();

    /** key: task_id. */
    private final Map<Integer, SearchTask> activeTasks = new HashMap<>();
    /**
     * key: search_id.
     * Added when the first task for this search is started,
     * removed when the last tasks for this search finishes.
     */
    private final Map<Integer, BookSearch> activeSearches = new HashMap<>();

    /** Flag indicating we're shutting down. */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, TaskProgress> progressByEngineId = new EnumMap<>(EngineId.class);

    /** Caches all created engines for reuse. */
    private final Map<EngineId, SearchEngine> engineCache = new EnumMap<>(EngineId.class);
    // There is a SINGLE/shared listener for ALL tasks!
    private final TaskListener<Book> searchTaskListener = new SearchTaskListener();
    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both active and disabled sites.
     */
    private List<Site> sites;
    /** Base message for progress updates. */
    @Nullable
    private String baseMessage;

    /** Cached string resource. */
    private String listElementPrefixString;

    /**
     * Process the message and start another task if required.
     *
     * @param taskId of task; this is the engine id.
     * @param result of a search;
     *               Will never be {@code null} for successful searches.
     *               MAY be {@code null} for cancelled searches.
     *               WILL be {@code null} for failed searches.
     */
    private synchronized void onSearchTaskFinished(final int taskId,
                                                   @Nullable final Book result) {

        // Lookup and rRemove the finished task from our list
        final SearchTask searchTask;
        synchronized (activeTasks) {
            searchTask = Objects.requireNonNull(activeTasks.remove(taskId),
                                                () -> ERROR_UNKNOWN_TASK + taskId);
        }

        // Lookup the search this task belongs to
        final BookSearch currentSearch;
        synchronized (activeSearches) {
            currentSearch = Objects.requireNonNull(
                    activeSearches.get(searchTask.getSearchId()),
                    () -> ERROR_UNKNOWN_SEARCH + searchTask.getSearchId());
        }

        final EngineId engineId = searchTask.getSearchEngine().getEngineId();

        if (BuildConfig.DEBUG /* always */) {
            debugSearchTaskFinished(taskId, currentSearch, engineId);
        }


        // ALWAYS store, even when null!
        // Presence of the site/task id in the map is an indication that the site was processed
        synchronized (currentSearch) {
            currentSearch.addResult(engineId, new SearchResult(engineId,
                                                               searchTask.getSearchBy(),
                                                               result));
        }

        // clear obsolete progress status
        synchronized (progressByEngineId) {
            progressByEngineId.remove(engineId);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Start new search(es) as needed/allowed.
        boolean searchStarted = false;

        if (!cancelRequested.get()) {
            //  update our listener with the current progress status
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
            }

            if (currentSearch.isWaitingForIsbnOrCode()) {
                if (result != null && result.hasIsbn()) {
                    // Start the remaining searches, even if they have run before.
                    // They will redo the search WITH the ISBN/code.
                    // Replace the search text with the (we hope) exact ISBN/code we just found.
                    // Worst case, explicitly use an empty string.
                    currentSearch.getCriteria().setIsbnText(result.getIsbn());
                    searchStarted = startConcurrentSearch(context, currentSearch);
                } else {
                    // sequentially start the next search which has not run yet.
                    searchStarted = startNextSearch(context, currentSearch);
                }
            }
        }

        // any searches still running or did we get cancelled?
        final boolean stopSearching;
        synchronized (activeTasks) {
            // if we didn't start a new search (which might not be active yet!),
            // and there are no previous searches still running
            // (or we got cancelled) then we are done.
            stopSearching = !searchStarted && (activeTasks.isEmpty() || cancelRequested.get());
        }

        if (stopSearching) {
            // debug: measure the time the searches took, don't include the post-processing
            final long processTime = System.nanoTime();

            synchronized (activeSearches) {
                activeSearches.remove(currentSearch.getId());
            }

            final Book book = currentSearch.accumulateResults(context, engineCache);
            final String searchErrors = currentSearch.accumulateErrors(context);
            if (searchErrors != null && !searchErrors.isEmpty()) {
                book.putString(BKEY_SEARCH_ERROR, searchErrors);
            }

            final LiveDataEvent<Book> message = LiveDataEvent.of(book);
            if (cancelRequested.get()) {
                searchCoordinatorCancelled.setValue(message);
            } else {
                searchCoordinatorFinished.setValue(message);
            }

            if (BuildConfig.DEBUG /* always */) {
                if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                                "searchId=" + currentSearch.getId(),
                                                "cancelled=" + cancelRequested.get(),
                                                "searchErrors=" + searchErrors);
                }
                if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                    currentSearch.debugDumpTimers(processTime);
                }
            }
        }
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskFailed(final int taskId,
                                                 @Nullable final Throwable e) {
        final SearchTask searchTask;
        synchronized (activeTasks) {
            searchTask = Objects.requireNonNull(activeTasks.get(taskId),
                                                () -> ERROR_UNKNOWN_TASK + taskId);
        }
        final BookSearch currentSearch;
        synchronized (activeSearches) {
            currentSearch = Objects.requireNonNull(
                    activeSearches.get(searchTask.getSearchId()),
                    () -> ERROR_UNKNOWN_SEARCH + searchTask.getSearchId());
        }

        synchronized (currentSearch) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(taskId),
                                                  () -> ERROR_UNKNOWN_TASK + taskId)
                                  .getSearchEngine().getEngineId();
            }
            // Always store, even if the Exception is null
            currentSearch.addError(engineId, e);
        }
        onSearchTaskFinished(taskId, null);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskProgress(@NonNull final TaskProgress message) {
        synchronized (progressByEngineId) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(message.taskId),
                                                  () -> ERROR_UNKNOWN_TASK + message.taskId)
                                  .getSearchEngine().getEngineId();
            }
            progressByEngineId.put(engineId, message);
        }
        // forward the accumulated progress
        synchronized (searchCoordinatorProgress) {
            searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
        }
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onSearchProgress() {
        return searchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     *
     * @return book data
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchFinished() {
        return searchCoordinatorFinished;
    }

    /**
     * The result if the user cancelled the search.
     *
     * @return book data found so far
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchCancelled() {
        return searchCoordinatorCancelled;
    }

    /**
     * Creates {@link TaskProgress} with the global/total progress of all tasks.
     *
     * @return instance
     */
    @NonNull
    private TaskProgress accumulateProgress() {

        int progressMax = 0;
        int progressCount = 0;

        final StringJoiner sb = new StringJoiner("\n");
        // Start with the base message if we have one.
        if (baseMessage != null && !baseMessage.isEmpty()) {
            sb.add(baseMessage);
        }

        synchronized (progressByEngineId) {
            if (!progressByEngineId.isEmpty()) {
                // Append each task message
                progressByEngineId
                        .values()
                        .stream()
                        .map(msg -> String.format(listElementPrefixString, msg.text))
                        .forEach(sb::add);

                // Accumulate the current & max values for each active task.
                for (final TaskProgress taskProgress : progressByEngineId.values()) {
                    progressMax += taskProgress.maxPosition;
                    progressCount += taskProgress.position;
                }
            }
        }

        return new TaskProgress(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                progressMax, progressCount, null);
    }


    @Override
    protected void onCleared() {
        cancel();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (sites == null) {
            sites = Site.Type.Data.getSites();
            listElementPrefixString = context.getString(R.string.list_element);
        }
    }

    /**
     * Cancel all searches.
     */
    public void cancel() {
        cancelRequested.set(true);
        synchronized (activeTasks) {
            activeTasks.values().forEach(SearchTask::cancel);
        }
    }

    public void cancelTask(@SuppressWarnings("unused") @IdRes final int taskId) {
        // reminder: this object, the SearchCoordinator is a pseudo task
        // we're only using "cancelTask" to conform with other usage
        cancel();
    }

    @Override
    public boolean isCancelled() {
        return cancelRequested.get();
    }

    /**
     * Check if a search task is already running.
     *
     * @return {@code true} if there is
     */
    public boolean isSearchActive() {
        synchronized (activeTasks) {
            return !activeTasks.isEmpty();
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with all sites <strong>active and disabled</strong>
     */
    @NonNull
    public List<Site> getSiteList() {
        return sites;
    }

    /**
     * Override the initial list. Can contain active and disabled equally.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final List<Site> sites) {
        this.sites.clear();
        this.sites.addAll(sites);
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional other criteria)
     * until we find an ISBN/code or until we searched all sites.
     * Once/if an ISBN/code is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN/code.
     *
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     *
     * @see #startSearch(Context, boolean, EngineId, BookSearch)
     */
    public int search(@NonNull final BookSearchCriteria criteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "init search");
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final BookSearch bookSearch = prepareSearch(context, criteria);

        if (criteria.hasSids() || criteria.hasValidIsbn()) {
            if (startConcurrentSearch(context, bookSearch)) {
                return bookSearch.getId();
            }
            return 0;

        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            if (startNextSearch(context, bookSearch)) {
                return bookSearch.getId();
            }
            return 0;
        }
    }

    /**
     * Search a single search on the given engine for the site specific book id (sid).
     *
     * @param engineId to use
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     *
     * @throws IllegalArgumentException if #sid was invalid
     * @see #startSearch(Context, boolean, EngineId, BookSearch)
     */
    public int searchByExternalId(@NonNull final EngineId engineId,
                                  @NonNull final BookSearchCriteria criteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "init searchByExternalId");
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final BookSearch bookSearch = prepareSearch(context, criteria);
        if (startSearch(context, false, engineId, bookSearch)) {
            return bookSearch.getId();
        }
        return 0;
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     *
     * @param context  Current context
     * @param criteria to search for
     *
     * @return new BookSearch instance
     *
     * @throws IllegalStateException    if the network is not already checked/available
     * @throws IllegalArgumentException if there are no criteria set
     */
    @NonNull
    private BookSearch prepareSearch(@NonNull final Context context,
                                     @NonNull final BookSearchCriteria criteria) {
        // reset flags
        cancelRequested.set(false);

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
                throw new IllegalStateException("Network should be checked before starting search");
            }

            if (isSearchActive()) {
                throw new IllegalStateException("a search is already running");
            }

            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("Nothing to search for");
            }
        }

        return new BookSearch(sites, criteria, new ResultsAccumulator(
                context, ServiceLocator.getInstance()::getLanguages));
    }

    /**
     * Start <strong>all</strong>> searches, which have not been run yet, in parallel.
     *
     * @param context    Current context
     * @param bookSearch to search for
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     *
     * @see #startSearch(Context, boolean, EngineId, BookSearch)
     */
    private boolean startConcurrentSearch(@NonNull final Context context,
                                          @NonNull final BookSearch bookSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startConcurrentSearch: " + bookSearch.getId());
        }

        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        boolean atLeastOneStarted = false;
        final List<EngineId> activeEngines = sites.stream()
                                                  .filter(Site::isActive)
                                                  .map(Site::getEngineId)
                                                  .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            if (!bookSearch.hasResult(engineId)) {
                if (startSearch(context, false, engineId, bookSearch)) {
                    atLeastOneStarted = true;
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start a single search on the next engine in the queue.
     *
     * @param context    Current context
     * @param bookSearch to search for
     *
     * @return {@code true} if a search was started, {@code false} if not
     *
     * @see #startSearch(Context, boolean, EngineId, BookSearch)
     */
    private boolean startNextSearch(@NonNull final Context context,
                                    @NonNull final BookSearch bookSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startNextSearch: " + bookSearch.getId());
        }

        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final List<EngineId> activeEngines = sites.stream()
                                                  .filter(Site::isActive)
                                                  .map(Site::getEngineId)
                                                  .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            if (!bookSearch.hasResult(engineId)) {
                final boolean started = startSearch(context, true, engineId, bookSearch);
                if (started) {
                    return true;
                }
                // else, loop to next site
            }
        }
        return false;
    }

    /**
     * Start the specified site search. This is where a search-task is actually started.
     * <p>
     * <strong>synchronized</strong> to make sure we start tasks in a serial manner.
     *
     * @param context           Current context
     * @param waitForIsbnOrCode flag
     * @param engineId          to search
     * @param bookSearch        to use
     *
     * @return {@code true} if the search was started.
     */
    private synchronized boolean startSearch(@NonNull final Context context,
                                             final boolean waitForIsbnOrCode,
                                             @NonNull final EngineId engineId,
                                             @NonNull final BookSearch bookSearch) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startSearch",
                                        "bookSearch=" + bookSearch.getId(),
                                        "searchEngine=" + engineId.name(),
                                        "waitForIsbnOrCode=" + waitForIsbnOrCode);
        }
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final SearchEngineConfig config = engineId.getConfig();
        // Sanity check; should not happen when we get here... flw
        if (config == null) {
            return false;
        }

        SearchEngine searchEngine = engineCache.get(engineId);
        if (searchEngine == null) {
            searchEngine = engineId.createSearchEngine(context);
            engineCache.put(engineId, searchEngine);
        } else {
            searchEngine.reset();
        }

        @Nullable
        final SearchTask task = SearchTask.createSearchTask(context,
                                                            bookSearch.getId(),
                                                            searchEngine,
                                                            bookSearch.getCriteria(),
                                                            searchTaskListener);

        if (task == null) {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        if (BuildConfig.DEBUG) {
            debugSearchTaskStarting(bookSearch, engineId, task, waitForIsbnOrCode);
        }

        bookSearch.setWaitingForIsbnOrCode(waitForIsbnOrCode);

        synchronized (activeSearches) {
            activeSearches.put(bookSearch.getId(), bookSearch);
        }
        synchronized (activeTasks) {
            activeTasks.put(task.getTaskId(), task);
        }

        task.startSearch();
        return true;
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
        this.baseMessage = baseMessage;
    }

    private void debugSearchTaskStarting(@NonNull final BookSearch bookSearch,
                                         @NonNull final EngineId engineId,
                                         @NonNull final SearchTask task,
                                         final boolean waitForIsbnOrCode) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startSearch",
                                        "bookSearch=" + bookSearch.getId(),
                                        "new-task=" + task.getTaskId(),
                                        "searchEngine=" + engineId.name(),
                                        "waitForIsbnOrCode=" + waitForIsbnOrCode);
        }
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            bookSearch.debugSetStartTime(task.getSearchEngine().getEngineId());
        }
    }

    private void debugSearchTaskFinished(final int taskId,
                                         final BookSearch currentSearch,
                                         final EngineId engineId) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            currentSearch.debugSetEndTime(engineId);
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                        "searchId=" + currentSearch.getId(),
                                        "taskId=" + taskId,
                                        engineId.name());
            final Logger logger = LoggerFactory.getLogger();
            synchronized (activeTasks) {
                for (final SearchTask task : activeTasks.values()) {
                    logger.d(TAG, "onSearchTaskFinished|running=" + task.getTaskId()
                                  + '|' + task.getSearchEngine().getEngineId().name());
                }
            }
        }
    }

    /**
     * Value class encapsulating
     * where a result came from + how the search was done + the result itself.
     * The result itself can be {@code null} if nothing was found.
     */
    static class SearchResult {

        @Nullable
        private final Book result;
        @NonNull
        private final EngineId engineId;
        @NonNull
        private final SearchEngine.SearchBy searchBy;

        SearchResult(@NonNull final EngineId engineId,
                     @NonNull final SearchEngine.SearchBy searchBy,
                     @Nullable final Book result) {
            this.engineId = engineId;
            this.searchBy = searchBy;
            this.result = result;
        }

        @NonNull
        EngineId getEngineId() {
            return engineId;
        }

        @NonNull
        SearchEngine.SearchBy getSearchBy() {
            return searchBy;
        }

        @NonNull
        Optional<Book> getResult() {
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        }
    }

    /**
     * Listener for <strong>individual</strong> search tasks.
     * There MUST only be ONE instance of this listener, shared between all tasks!
     * It serves as a translation layer between the standard TaskListener
     * and what the SearchCoordinator needs.
     * <p>
     * Dev. Note: and it's an inner class because if we simply create it as an anonymous class
     * as we normally would/should, or ...
     * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
     * Each time we format the code, methods and variables jump around.
     * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
     */
    private final class SearchTaskListener
            implements TaskListener<Book> {

        @Override
        public void onProgress(@NonNull final TaskProgress message) {
            onSearchTaskProgress(message);
        }

        @Override
        public void onFinished(final int taskId,
                               @Nullable final Book result) {
            // The result MUST NOT be null
            onSearchTaskFinished(taskId, Objects.requireNonNull(result, "result"));
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final Book result) {
            onSearchTaskFinished(taskId, result);
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Throwable e) {
            onSearchTaskFailed(taskId, e);
        }
    }
}

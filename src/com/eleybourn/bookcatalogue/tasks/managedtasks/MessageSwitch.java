/*
 * @copyright 2012 Philip Warner
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
package com.eleybourn.bookcatalogue.tasks.managedtasks;

import android.annotation.SuppressLint;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Switchboard class for disconnecting listener instances from task instances. Maintains
 * separate lists and each 'sender' queue maintains a last-message for re-transmission
 * when a listener instance (re)connects.
 * <p>
 * Usage:
 * <p>
 * A sender (typically a background task, thread or thread manager) registers itself and is
 * assigned a unique ID. The creator of the sender uses the ID as the key for later retrieval.
 * <p>
 * The listener must have access to the unique ID and use that to register themselves.
 * <p>
 * The listener should call {@link #addListener}, {@link #removeListener}
 * or {@link #getController} as necessary.
 * <p>
 * ENHANCE: Allow fixed sender IDs to ensure uniqueness / allow multiple senders for specific IDs
 *
 * @param <T> The Class (a listener interface) of message that this switchboard sends
 * @param <U> The Class of controller object made available to listeners.
 *            The controller gives access to the sender.
 *
 * @author pjw
 */
public class MessageSwitch<T, U> {

    /** Handler object for posting to main thread and for testing if running on UI thread. */
    private static final Handler mHandler = new Handler();
    /** ID counter for unique sender IDs; set > 0 to allow for possible future static senders. */
    @NonNull
    private static final AtomicLong SENDER_ID_COUNTER = new AtomicLong(1024L);

    /** List of message sources. */
    @SuppressLint("UseSparseArrays")
    private final Map<Long, MessageSender<U>> mSenders = Collections.synchronizedMap(
            new HashMap<Long, MessageSender<U>>());

    /** List of all messages in the message queue, both messages and replies. */
    private final LinkedBlockingQueue<RoutingSlip> mMessageQueue = new LinkedBlockingQueue<>();

    /** List of message listener queues. */
    @SuppressLint("UseSparseArrays")
    private final Map<Long, MessageListeners> mListeners = Collections.synchronizedMap(
            new HashMap<Long, MessageListeners>());

    /**
     * Register a new sender and it's controller object.
     *
     * @return the unique ID for this sender
     */
    @NonNull
    public Long createSender(@NonNull final U controller) {
        MessageSender<U> s = new MessageSenderImpl(controller);
        mSenders.put(s.getId(), s);
        return s.getId();
    }

    /**
     * Add a listener for the specified sender ID.
     *
     * @param senderId    ID of sender to which the listener listens
     * @param listener    Listener object
     * @param deliverLast If true, send the last message (if any) to this listener
     */
    public void addListener(@NonNull final Long senderId,
                            @NonNull final T listener,
                            final boolean deliverLast) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "addListener",listener + "|senderId=" + senderId);
        }
        // Add the listener to the queue, creating the queue if necessary
        MessageListeners queue;
        synchronized (mListeners) {
            queue = mListeners.get(senderId);
            if (queue == null) {
                queue = new MessageListeners();
                mListeners.put(senderId, queue);
            }
            queue.add(listener);
        }
        // Try to deliver last message if requested
        if (deliverLast) {
            final MessageRoutingSlip routingSlip = queue.getLastMessage();
            // If there was a message then send to the passed listener
            if (routingSlip != null) {
                // Do it on the UI thread.
                if (mHandler.getLooper().getThread() == Thread.currentThread()) {
                    if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                        Logger.info(this,"addListener",
                                    "|UI thread|delivering to listener: " +
                                            listener + "|msg=" + routingSlip.message.toString());
                    }
                    routingSlip.message.deliver(listener);
                } else {
                    if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                        Logger.info(this,"addListener",
                                    "|post runnable|delivering to listener: " +
                                            listener + "|msg=" + routingSlip.message.toString());
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            routingSlip.message.deliver(listener);
                        }
                    });
                }
            }
        }
    }

    /**
     * Remove the specified listener from the specified queue.
     */
    public void removeListener(@NonNull final Long senderId,
                               @NonNull final T listener) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "removeListener","senderId=" +
                    senderId + '|' + listener);
        }
        synchronized (mListeners) {
            MessageListeners queue = mListeners.get(senderId);
            if (queue != null) {
                queue.remove(listener);
            }
        }
    }

    /**
     * Send a message to a queue.
     *
     * @param senderId Queue ID
     * @param message  Message to send
     */
    public void send(@NonNull final Long senderId,
                     @NonNull final Message<T> message) {
        if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
            Logger.info(this, "send","senderId=" +
                    senderId + "|message: " + message);
        }
        // Create a routing slip
        RoutingSlip m = new MessageRoutingSlip(senderId, message);
        // Add to queue
        synchronized (mMessageQueue) {
            mMessageQueue.add(m);
        }
        // Process queue
        startProcessingMessages();
    }


    /**
     * Get the controller object associated with a sender ID.
     *
     * @param senderId ID of sender
     *
     * @return Controller object of type 'U'
     */
    @Nullable
    public U getController(@NonNull final Long senderId) {
        MessageSender<U> sender = mSenders.get(senderId);
        if (sender != null) {
            return sender.getController();
        } else {
            return null;
        }
    }

    /**
     * Remove a sender and it's queue.
     */
    private void removeSender(@NonNull final MessageSender<U> s) {
        synchronized (mSenders) {
            mSenders.remove(s.getId());
        }
    }

    /**
     * If in UI thread, then process the queue, otherwise post a new runnable
     * to process the queued messages.
     */
    private void startProcessingMessages() {
        if (mHandler.getLooper().getThread() == Thread.currentThread()) {
            processMessages();
        } else {
            mHandler.post(new Runnable() {
                              @Override
                              public void run() {
                                  processMessages();
                              }
                          }
            );
        }
    }

    /**
     * Process the queued messages.
     */
    private void processMessages() {
        RoutingSlip m;
        do {
            synchronized (mMessageQueue) {
                m = mMessageQueue.poll();
            }
            if (m == null) {
                break;
            }

            m.deliver();
        } while (true);
    }

    /**
     * Interface that must be implemented by any message that will be sent via send().
     */
    public interface Message<T> {

        /**
         * Method to deliver a message.
         *
         * @param listener Listener to who message must be delivered
         *
         * @return <tt>true</tt> if message should not be delivered to any other listeners or
         * stored for delivery as 'last message'. Should only return <tt>false</tt> if
         * the message has been handled and would break the app if delivered more than once.
         */
        boolean deliver(@NonNull T listener);
    }

    /**
     * Interface for all messages sent to listeners.
     *
     * @param <U> Arbitrary class that will be responsible for the message
     *
     * @author pjw
     */
    private interface MessageSender<U>
            extends AutoCloseable {

        Long getId();

        @Override
        void close();

        @NonNull
        U getController();
    }

    /**
     * Interface implemented by all routing slips objects.
     */
    private interface RoutingSlip {

        void deliver();
    }

    /**
     * Class used to hold a list of listener objects.
     */
    private class MessageListeners
            implements Iterable<T> {

        /** Weak refs to all listeners. */
        private final List<WeakReference<T>> mList = new ArrayList<>();
        /** Last message sent. */
        @Nullable
        private MessageRoutingSlip mLastMessage;

        @Nullable
        MessageRoutingSlip getLastMessage() {
            return mLastMessage;
        }

        void setLastMessage(@Nullable final MessageRoutingSlip m) {
            mLastMessage = m;
        }

        /** Add a listener to this queue. */
        void add(@NonNull final T listener) {
            synchronized (mList) {
                mList.add(new WeakReference<>(listener));
            }
        }

        /**
         * Remove a listener from this queue; also removes dead references.
         *
         * @param listener Listener to be removed
         */
        void remove(@NonNull final T listener) {
            synchronized (mList) {
                // List of refs to be removed
                List<WeakReference<T>> toRemove = new ArrayList<>();
                // Loop the list for matches or dead refs
                for (WeakReference<T> w : mList) {
                    T l = w.get();
                    if (l == null || l == listener) {
                        toRemove.add(w);
                    }
                }
                // Remove all listeners we found
                for (WeakReference<T> w : toRemove) {
                    mList.remove(w);
                }
            }
        }

        /**
         * Return an iterator to a *copy* of the valid underlying elements.
         * This means that callers can make changes to the underlying list with impunity,
         * and more importantly they can iterate over type T, rather than a bunch of weak
         * references to T.
         * <p>
         * Side-effect: removes invalid listeners.
         */
        @NonNull
        @Override
        public Iterator<T> iterator() {
            List<T> list = new ArrayList<>();
            List<WeakReference<T>> toRemove = null;
            synchronized (mList) {
                for (WeakReference<T> w : mList) {
                    T listener = w.get();
                    if (listener != null) {
                        list.add(listener);
                    } else {
                        if (toRemove == null) {
                            toRemove = new ArrayList<>();
                        }
                        toRemove.add(w);
                    }
                }
                if (toRemove != null) {
                    for (WeakReference<T> w : toRemove) {
                        mList.remove(w);
                    }
                }
            }
            return list.iterator();
        }

        //private final ReentrantLock mPopLock = new ReentrantLock();
        //ReentrantLock getLock() {
        //  return mPopLock;
        //}
    }

    /**
     * RoutingSlip to deliver a Message object to all associated listeners.
     */
    private class MessageRoutingSlip
            implements RoutingSlip {

        /** Destination queue (sender ID). */
        @NonNull
        final Long destination;
        /** Message to deliver. */
        @NonNull
        final Message<T> message;

        /** Constructor. */
        MessageRoutingSlip(@NonNull final Long destination,
                           @NonNull final Message<T> message) {
            this.destination = destination;
            this.message = message;
        }

        /** Deliver message to all members of queue of sender. */
        @Override
        public void deliver() {
            // Iterator for iterating queue
            Iterator<T> queueIterator = null;

            MessageListeners queue;
            // Find the queue and get the iterator
            synchronized (mListeners) {
                // Queue for given ID
                queue = mListeners.get(destination);
                if (queue != null) {
                    queue.setLastMessage(this);
                    queueIterator = queue.iterator();
                }
            }
            // If we have an iterator, send the message to each listener
            if (queueIterator != null) {
                boolean handled = false;
                while (queueIterator.hasNext()) {
                    T listener = queueIterator.next();
                    try {
                        if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                            Logger.info(this,"deliver",
                                        "queueIterator|listener=" +
                                                listener + "|msg=" + message.toString());
                        }
                        if (message.deliver(listener)) {
                            handled = true;
                            break;
                        }

                    } catch (RuntimeException e) {
                        Logger.error(e, "Error delivering message to listener");
                    }
                }
                if (handled) {
                    queue.setLastMessage(null);
                }
            }
        }
    }

    /**
     * Implementation of Message sender object.
     */
    private class MessageSenderImpl
            implements MessageSender<U> {

        // mId will be used as a key in maps, while 'long' would work,
        // let's be consistent and use Long.
        private final Long mId = SENDER_ID_COUNTER.incrementAndGet();
        @NonNull
        private final U mController;

        /** Constructor. */
        MessageSenderImpl(@NonNull final U controller) {
            mController = controller;
        }

        @Override
        public Long getId() {
            return mId;
        }

        @NonNull
        @Override
        public U getController() {
            return mController;
        }

        /** Close and delete this sender. */
        @Override
        public void close() {
            synchronized (mSenders) {
                MessageSwitch.this.removeSender(this);
            }
        }
    }
}

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package jdk.internal.misc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks threads to help detect reentrancy without using ThreadLocal variables.
 * A thread invokes the {@code begin} or {@code tryBegin} methods at the start
 * of a block, and the {@code end} method at the end of a block.
 */
public class ThreadTracker {

    /**
     * A reference to a Thread that is suitable for use as a key in a collection.
     * The hashCode/equals methods do not invoke the Thread hashCode/equals method
     * as they may run arbitrary code and/or leak references to Thread objects.
     */

    private final class ThreadRef {
        private final Thread thread;

        public ThreadRef(Thread thread) {
            this.thread = thread;
        }

        public Thread thread() {
            return thread;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(thread.getId());
        }
        @Override
        public boolean equals(Object obj) {
            return obj instanceof ThreadRef ?
                    ((ThreadRef) obj).thread == this.thread : false;
        }

        @Override
        public String toString() {
            String threadToStringRes = thread != null ? thread.toString() : null;
            return "ThreadRef ["+threadToStringRes+"]";
        }
    }

    private final Set<ThreadRef> threads = ConcurrentHashMap.newKeySet();

    /**
     * Adds the current thread to thread set if not already in the set.
     * Returns a key to remove the thread or {@code null} if already in the set.
     */
    public Object tryBegin() {
        ThreadRef threadRef = new ThreadRef(Thread.currentThread());
        return threads.add(threadRef) ? threadRef : null;
    }

    /**
     * Adds the current thread to thread set if not already in the set.
     * Returns a key to remove the thread.
     */
    public Object begin() {
        ThreadRef threadRef = new ThreadRef(Thread.currentThread());
        boolean added = threads.add(threadRef);
        assert added;
        return threadRef;
    }

    /**
     * Removes the thread identified by the key from the thread set.
     */
    public void end(Object key) {
        ThreadRef threadRef = (ThreadRef) key;
        assert threadRef.thread() == Thread.currentThread();
        boolean removed = threads.remove(threadRef);
        assert removed;
    }

    /**
     * Returns true if the given thread is tracked.
     */
    public boolean contains(Thread thread) {
        ThreadRef threadRef = new ThreadRef(thread);
        return threads.contains(threadRef);
    }
}

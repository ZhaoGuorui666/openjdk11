/*
 * Copyright (c) 1996, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.http;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jdk.internal.misc.InnocuousThread;
import sun.security.action.GetIntegerAction;
import sun.net.www.protocol.http.HttpURLConnection;
import sun.util.logging.PlatformLogger;

/**
 * A class that implements a cache of idle Http connections for keep-alive
 *
 * @author Stephen R. Pietrowicz (NCSA)
 * @author Dave Brown
 */
public class KeepAliveCache
    extends HashMap<KeepAliveKey, ClientVector>
    implements Runnable {
    private static final long serialVersionUID = -2937172892064557949L;

    // Keep alive time set according to priority specified here:
    // 1. If server specifies a time with a Keep-Alive header
    // 2. If user specifies a time with system property below
    // 3. Default values which depend on proxy vs server and whether
    //    a Connection: keep-alive header was sent by server

    // name suffixed with "server" or "proxy"
    private static final String keepAliveProp = "http.keepAlive.time.";

    private static final int userKeepAliveServer;
    private static final int userKeepAliveProxy;

    static final PlatformLogger logger = HttpURLConnection.getHttpLogger();

    @SuppressWarnings("removal")
    static int getUserKeepAliveSeconds(String type) {
        int v = AccessController.doPrivileged(
            new GetIntegerAction(keepAliveProp+type, -1)).intValue();
        return v < -1 ? -1 : v;
    }

    static {
        userKeepAliveServer = getUserKeepAliveSeconds("server");
        userKeepAliveProxy = getUserKeepAliveSeconds("proxy");
    }


    /* maximum # keep-alive connections to maintain at once
     * This should be 2 by the HTTP spec, but because we don't support pipe-lining
     * a larger value is more appropriate. So we now set a default of 5, and the value
     * refers to the number of idle connections per destination (in the cache) only.
     * It can be reset by setting system property "http.maxConnections".
     */
    static final int MAX_CONNECTIONS = 5;
    static int result = -1;
    static int getMaxConnections() {
        if (result == -1) {
            result = AccessController.doPrivileged(
                new GetIntegerAction("http.maxConnections", MAX_CONNECTIONS))
                .intValue();
            if (result <= 0) {
                result = MAX_CONNECTIONS;
            }
        }
        return result;
    }

    static final int LIFETIME = 5000;

    private Thread keepAliveTimer = null;

    /**
     * Constructor
     */
    public KeepAliveCache() {}

    /**
     * Register this URL and HttpClient (that supports keep-alive) with the cache
     * @param url  The URL contains info about the host and port
     * @param http The HttpClient to be cached
     */
    public void put(final URL url, Object obj, HttpClient http) {
        // this method may need to close an HttpClient, either because
        // it is not cacheable, or because the cache is at its capacity.
        // In the latter case, we close the least recently used client.
        // The client to close is stored in oldClient, and is closed
        // after cacheLock is released.
        HttpClient oldClient = null;

        synchronized (this) {
            boolean startThread = (keepAliveTimer == null);
            if (!startThread) {
                if (!keepAliveTimer.isAlive()) {
                    startThread = true;
                }
            }

            if (startThread) {
                clear();
                /* Unfortunately, we can't always believe the keep-alive timeout we got
                 * back from the server.  If I'm connected through a Netscape proxy
                 * to a server that sent me a keep-alive
                 * time of 15 sec, the proxy unilaterally terminates my connection
                 * The robustness to get around this is in HttpClient.parseHTTP()
                 */
                final KeepAliveCache cache = this;
                AccessController.doPrivileged(new PrivilegedAction<>() {
                    public Void run() {
                        keepAliveTimer = InnocuousThread.newSystemThread("Keep-Alive-Timer", cache);
                        keepAliveTimer.setDaemon(true);
                        keepAliveTimer.setPriority(Thread.MAX_PRIORITY - 2);
                        keepAliveTimer.start();
                        return null;
                    }
                });
            }

            KeepAliveKey key = new KeepAliveKey(url, obj);
            ClientVector v = super.get(key);

            if (v == null) {
                int keepAliveTimeout = http.getKeepAliveTimeout();
                if (keepAliveTimeout == 0) {
                    keepAliveTimeout = getUserKeepAlive(http.getUsingProxy());
                    if (keepAliveTimeout == -1) {
                        // same default for server and proxy
                        keepAliveTimeout = 5;
                    }
                } else if (keepAliveTimeout == -1) {
                    keepAliveTimeout = getUserKeepAlive(http.getUsingProxy());
                    if (keepAliveTimeout == -1) {
                        // different default for server and proxy
                        keepAliveTimeout = http.getUsingProxy() ? 60 : 5;
                    }
                } else if (keepAliveTimeout == -2) {
                    keepAliveTimeout = 0;
                }
                // at this point keepAliveTimeout is the number of seconds to keep
                // alive, which could be 0, if the user specified 0 for the property
                assert keepAliveTimeout >= 0;
                if (keepAliveTimeout == 0) {
                    oldClient = http;
                } else {
                    v = new ClientVector(keepAliveTimeout * 1000);
                    v.put(http);
                    super.put(key, v);
                }
            } else {
                oldClient = v.put(http);
            }
        }
        // close after releasing locks
        if (oldClient != null) {
            oldClient.closeServer();
        }
    }

    // returns the keep alive set by user in system property or -1 if not set
    private static int getUserKeepAlive(boolean isProxy) {
        return isProxy ? userKeepAliveProxy : userKeepAliveServer;
    }

    /* called by a clientVector thread when all its connections have timed out
     * and that vector of connections should be removed.
     */
    synchronized void removeVector(KeepAliveKey k) {
        super.remove(k);
    }

    /**
     * Check to see if this URL has a cached HttpClient
     */
    public synchronized HttpClient get(URL url, Object obj) {
        KeepAliveKey key = new KeepAliveKey(url, obj);
        ClientVector v = super.get(key);
        if (v == null) { // nothing in cache yet
            return null;
        }
        return v.get();
    }

    /* Sleeps for an alloted timeout, then checks for timed out connections.
     * Errs on the side of caution (leave connections idle for a relatively
     * short time).
     */
    @Override
    public void run() {
        do {
            try {
                Thread.sleep(LIFETIME);
            } catch (InterruptedException e) {}
            List<HttpClient> closeList = null;

            // Remove all outdated HttpClients.
            synchronized (this) {
                long currentTime = System.currentTimeMillis();
                List<KeepAliveKey> keysToRemove = new ArrayList<>();

                for (KeepAliveKey key : keySet()) {
                    ClientVector v = get(key);
                    synchronized (v) {
                        KeepAliveEntry e = v.peekLast();
                        while (e != null) {
                            if ((currentTime - e.idleStartTime) > v.nap) {
                                v.pollLast();
                                if (closeList == null) {
                                    closeList = new ArrayList<>();
                                }
                                closeList.add(e.hc);
                            } else {
                                break;
                            }
                            e = v.peekLast();
                        }

                        if (v.isEmpty()) {
                            keysToRemove.add(key);
                        }
                    }
                }

                for (KeepAliveKey key : keysToRemove) {
                    removeVector(key);
                }

            }
            // close connections outside cacheLock
            if (closeList != null) {
                for (HttpClient hc : closeList) {
                    hc.closeServer();
                }
            }
        } while (!isEmpty());
    }

    /*
     * Do not serialize this class!
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
        throw new NotSerializableException();
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
        throw new NotSerializableException();
    }
}

/* LIFO order for reusing HttpClients. Most recent entries at the front.
 * If > maxConns are in use, discard oldest.
 */
class ClientVector extends ArrayDeque<KeepAliveEntry> {
    private static final long serialVersionUID = -8680532108106489459L;

    // sleep time in milliseconds, before cache clear
    int nap;

    ClientVector(int nap) {
        this.nap = nap;
    }

    /* return a still valid, idle HttpClient */
    synchronized HttpClient get() {
        KeepAliveEntry e = peekFirst();
        if (e == null) {
            return null;
        }

        long currentTime = System.currentTimeMillis();
        if ((currentTime - e.idleStartTime) > nap) {
            return null; // all connections stale - will be cleaned up later
        } else {
            pollFirst();
            if (KeepAliveCache.logger.isLoggable(PlatformLogger.Level.FINEST)) {
                String msg = "cached HttpClient was idle for "
                        + Long.toString(currentTime - e.idleStartTime);
                KeepAliveCache.logger.finest(msg);
            }
            return e.hc;
        }
    }

    synchronized HttpClient put(HttpClient h) {
        HttpClient staleClient = null;
        assert KeepAliveCache.getMaxConnections() > 0;
        if (size() >= KeepAliveCache.getMaxConnections()) {
            // remove oldest connection
            staleClient = removeLast().hc;
        }
        addFirst(new KeepAliveEntry(h, System.currentTimeMillis()));

        // close after releasing the locks
        return staleClient;
    }

    /*
     * Do not serialize this class!
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
        throw new NotSerializableException();
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
        throw new NotSerializableException();
    }
}

class KeepAliveKey {
    private final String      protocol;
    private final String      host;
    private final int         port;
    private final Object      obj; // additional key, such as socketfactory

    /**
     * Constructor
     *
     * @param url the URL containing the protocol, host and port information
     */
    public KeepAliveKey(URL url, Object obj) {
        this.protocol = url.getProtocol();
        this.host = url.getHost();
        this.port = url.getPort();
        this.obj = obj;
    }

    /**
     * Determine whether or not two objects of this type are equal
     */
    @Override
    public boolean equals(Object obj) {
        if ((obj instanceof KeepAliveKey) == false)
            return false;
        KeepAliveKey kae = (KeepAliveKey)obj;
        return host.equals(kae.host)
            && (port == kae.port)
            && protocol.equals(kae.protocol)
            && this.obj == kae.obj;
    }

    /**
     * The hashCode() for this object is the string hashCode() of
     * concatenation of the protocol, host name and port.
     */
    @Override
    public int hashCode() {
        String str = protocol+host+port;
        return this.obj == null? str.hashCode() :
            str.hashCode() + this.obj.hashCode();
    }
}

class KeepAliveEntry {
    final HttpClient hc;
    final long idleStartTime;

    KeepAliveEntry(HttpClient hc, long idleStartTime) {
        this.hc = hc;
        this.idleStartTime = idleStartTime;
    }
}

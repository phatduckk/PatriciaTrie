package com.jeraff.patricia.server.ops;

import com.jeraff.patricia.conf.JDBC;
import com.jeraff.patricia.server.analyzer.DistanceComparator;
import com.jeraff.patricia.server.analyzer.PartialMatchAnalyzer;
import com.jeraff.patricia.conf.Core;
import org.limewire.collection.PatriciaTrie;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PatriciaOps {
    private static final Logger log = Logger.getLogger(PatriciaOps.class.getCanonicalName());
    private static final int NUM_PREFIX_MATCHES = 10;
    private static final int DEFAULT_THREADS = 20;

    private JDBC jdbc;
    private PatriciaTrie<String, String> patriciaTrie;
    private PartialMatchAnalyzer analyzer;
    private ExecutorService putPool;
    private ExecutorService dbPool;
    private Connection dbConnection;
    private Core core;

    public PatriciaOps(final Core core, PatriciaTrie<String, String> patriciaTrie) {
        this.patriciaTrie = patriciaTrie;
        this.analyzer = new PartialMatchAnalyzer();
        this.core = core;

        final String canonicalCoreName = core.canonicalName();
        this.putPool = Executors.newFixedThreadPool(DEFAULT_THREADS, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                return new Thread(runnable, "PatriciaOps.PutPool." + canonicalCoreName);
            }
        });

        if (core.getJdbc() != null) {
            this.dbPool = Executors.newFixedThreadPool(DEFAULT_THREADS, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    return new Thread(runnable, "PatriciaOps.DBPool." + canonicalCoreName);
                }
            });

            try {
                jdbc = core.getJdbc();
                dbConnection = createDBConnection(core);
            } catch (SQLException e) {
                log.log(Level.SEVERE, "Couldn't create DB connection", e);
                throw new RuntimeException(e);
            }
        }
    }

    private Connection createDBConnection(Core core) throws SQLException {
        final Properties properties = new Properties();
        properties.put("user", core.getJdbc().getUser());
        properties.put("password", core.getJdbc().getPassword());

        Connection conn = DriverManager.getConnection(jdbc.getUrl(), properties);
        conn.setAutoCommit(true);
        return conn;
    }

    public String firstKey() {
        return patriciaTrie.firstKey();
    }

    public String lastKey() {
        return patriciaTrie.lastKey();
    }

    public int size() {
        return patriciaTrie.size();
    }

    public HashMap<String, ArrayList<String>> put(String[] strings, boolean persist) {
        final int length = strings.length;
        final HashMap<String, ArrayList<String>> result = new HashMap<String, ArrayList<String>>(length);

        for (String string : strings) {
            final ArrayList<String> keys = new ArrayList<String>();

            final Set<Map.Entry<String, String>> indexEntries = analyzer.getIndexEntry(string);
            for (Map.Entry<String, String> entry : indexEntries) {
                patriciaTrie.put(entry.getKey(), string);
                keys.add(entry.getKey());
            }

            if (result != null) {
                result.put(string, keys);
                if (persist && dbConnection != null) {
                    persistString(string);
                }
            }
        }

        return result;
    }

    public HashMap<String, ArrayList<String>> put(String[] strings) {
        return put(strings, true);
    }

    public List<String> getPrefixedBy(String prefix) {
        final SortedMap<String, String> prefixedBy = patriciaTrie.getPrefixedBy(analyzer.getPrefixSearchKey(prefix));

        if (prefixedBy.isEmpty()) {
            return new ArrayList<String>();
        }

        List<String> result = new ArrayList<String>(new TreeSet<String>(prefixedBy.values()));
        final int total = result.size();

        if (total > NUM_PREFIX_MATCHES) {
            result = result.subList(0, NUM_PREFIX_MATCHES);
        }

        Collections.sort(result, new DistanceComparator(prefix, analyzer));
        return result;
    }

    public int getPrefixedByCount(String string) {
        return getPrefixedBy(string).size();
    }

    public HashMap<String, String> remove(String[] strings) {
        final int length = strings.length;
        final HashMap<String, String> result = new HashMap<String, String>(length);

        for (String string : strings) {
            final Set<Map.Entry<String, String>> entries = analyzer.getIndexEntry(string);
            for (Map.Entry<String, String> entry : entries) {
                result.put(string, patriciaTrie.remove(entry.getKey()));
            }
        }

        return result;
    }

    public void persistString(final String str) {
        dbPool.submit(new Runnable() {
            private String insertString = String.format(
                    "INSERT INTO %s(%s, %s) VALUES(?, ?) ON DUPLICATE KEY UPDATE %s=?",
                    jdbc.getTable(), jdbc.getHash(), jdbc.getS(), jdbc.getS());

            @Override
            public void run() {
                if (dbConnection == null) {
                    log.log(Level.FINE, "No DB connection available.");
                    return;
                }

                try {
                    if (dbConnection.isClosed()) {
                        dbConnection = createDBConnection(core);
                    }

                    final PreparedStatement statement = dbConnection.prepareStatement(insertString);
                    statement.setString(1, analyzer.getHash(str));
                    statement.setString(2, str);
                    statement.setString(3, str);
                    statement.execute();
                } catch (SQLException e) {
                    log.log(Level.WARNING, "Couldn't execute query", e);
                }
            }
        });
    }

    public void enqueue(final String[] strings) {
        putPool.submit(new Runnable() {
            @Override
            public void run() {
                for (String string : strings) {
                    if (log.isLoggable(Level.INFO)) {
                        log.log(Level.INFO, "Working on {0} strings", strings.length);
                    }

                    final Set<Map.Entry<String, String>> indexEntries = analyzer.getIndexEntry(string);
                    for (Map.Entry<String, String> entry : indexEntries) {
                        final String key = entry.getKey();

                        if (patriciaTrie.containsKey(key)) {
                            final String existing = patriciaTrie.get(key);
                            final String winner = analyzer.getPreferred(existing, string);
                            if (!winner.equals(existing)) {
                                put(new String[]{winner});
                            }
                        } else {
                            put(new String[]{string});
                        }
                    }
                }
            }
        });
    }
}

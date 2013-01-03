package com.jeraff.patricia.conf;

import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Config {
    protected static final Logger log = Logger.getLogger(Config.class.getCanonicalName());

    public static final String CONNECTOR = "connector";
    public static final String CONNECTOR_ACCEPTORS = "acceptors";

    public static final String PATRICIA_PROP_PREFIX = "patricia.";
    public static final String PROP_CONFIG_FILE = PATRICIA_PROP_PREFIX + "conf";

    private Connector connector;
    private List<Core> cores;
    private JDBC jdbc;
    private long time;
    private String confFilePath;
    private boolean needsIndexHandler;

    public Config() {
    }

    public static Config instance(Properties properties) throws Exception {
        final String confFilePath = properties.getProperty(PROP_CONFIG_FILE);
        final HashMap<String, Object> conf = new HashMap<String, Object>();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        conf.put("time", System.currentTimeMillis());
        conf.put(confFilePath, confFilePath);

        if (confFilePath != null) {
            final HashMap map = mapper.readValue(new File(confFilePath), HashMap.class);
            conf.putAll(map);
        }

        handleSystemProperties(properties, conf);
        final Config config = mapper.readValue(mapper.writeValueAsString(conf), Config.class);
        config.setupCores();

        return config;
    }

    private void setupCores() {
        Set<String> paths = new HashSet<String>(cores.size());
        for (Core core : cores) {
            final String path = core.getPath();
            if (paths.contains(path)) {
                throw new RuntimeException("Invalid core config. Following path is shared by 2 cores: " + path);
            }

            paths.add(path);
        }

        if (! paths.contains("/")) {
            needsIndexHandler = true;
        }
    }

    public String getConfigFileContent() throws IOException {
        if (confFilePath == null) {
            return null;
        }

        FileInputStream in = null;

        try {
            in = new FileInputStream(new File(confFilePath));
            InputStreamReader inR = new InputStreamReader(in);
            BufferedReader buf = new BufferedReader(inR);
            List<String> lines = new ArrayList<String>();
            String line;

            while ((line = buf.readLine()) != null) {
                lines.add(line);
            }

            return StringUtils.join(lines, "\n");
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private static void handleSystemProperties(Properties properties, HashMap<String, Object> confMap) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            final Object key = entry.getKey();
            if (!(key instanceof String)) {
                continue;
            }

            String name = (String) key;
            if (!name.startsWith(PATRICIA_PROP_PREFIX)) {
                continue;
            }

            final Object value = entry.getValue();
            final List<String> strings = new ArrayList<String>(Arrays.asList(name.split("\\.")));
            final Iterator<String> iterator = strings.iterator();
            iterator.next();
            iterator.remove();

            Map<String, Object> node = confMap;
            while (iterator.hasNext()) {
                final String next = iterator.next();

                if (iterator.hasNext()) {
                    if (!node.containsKey(next)) {
                        node.put(next, new HashMap<String, Object>());
                    }

                    node = (Map<String, Object>) node.get(next);
                } else {
                    node.put(next, value);
                }

                iterator.remove();
            }
        }
    }

    public void configConnector(SelectChannelConnector channelConnector) {
        if (connector == null) {
            return;
        }

        try {
            BeanMap map = new BeanMap(connector);
            BeanUtils.populate(channelConnector, map);
        } catch (Exception e) {
            String error = "Could not configure channelConnector";
            log.log(Level.SEVERE, error, e);
            throw new RuntimeException(error, e);
        }
    }


    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public List<Core> getCores() {
        return cores;
    }

    public void setCores(List<Core> cores) {
        this.cores = cores;
    }

    public JDBC getJdbc() {
        return jdbc;
    }

    public void setJdbc(JDBC jdbc) {
        this.jdbc = jdbc;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getConfFilePath() {
        return confFilePath;
    }

    public void setConfFilePath(String confFilePath) {
        this.confFilePath = confFilePath;
    }

    public boolean isNeedsIndexHandler() {
        return needsIndexHandler;
    }

    public void setNeedsIndexHandler(boolean needsIndexHandler) {
        this.needsIndexHandler = needsIndexHandler;
    }
}

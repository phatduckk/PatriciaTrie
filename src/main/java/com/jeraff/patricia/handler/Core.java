package com.jeraff.patricia.handler;

import com.google.gson.internal.StringMap;
import com.jeraff.patricia.analyzer.PartialMatchAnalyzer;
import com.jeraff.patricia.util.GsonIgnore;
import org.apache.commons.lang.StringUtils;

public class Core {
    private String contextPath;
    @GsonIgnore
    private Class analyzerClass;
    private String analyzer;

    private static final String KEY_ANALYZER = "analyzer";

    public Core(String contextPath) {
        analyzerClass = PartialMatchAnalyzer.class;
        analyzer = PartialMatchAnalyzer.class.getCanonicalName();

        setContextPath(contextPath);
    }

    public Core(String contextPath, StringMap<String> map) throws ClassNotFoundException {
        analyzerClass = Class.forName(map.get(KEY_ANALYZER));
        analyzer = map.get(KEY_ANALYZER);
        setContextPath(contextPath);
    }

    private void setContextPath(String contextPath) {
        final String s = String.format("/%s", StringUtils.strip(contextPath, "/"));
        this.contextPath = s;
    }

    public String getContextPath() {
        return contextPath;
    }

    public Class getAnalyzerClass() {
        return analyzerClass;
    }

    private String makeUrl(String path) {
        final String strip = StringUtils.strip(contextPath, "/");
        if (StringUtils.isBlank(strip)) {
            return String.format("/%s/", StringUtils.strip(path, "/"));
        } else {
            return String.format("/%s/%s/", strip, StringUtils.strip(path, "/"));
        }
    }

    public String getAddUrl() {
        return makeUrl("add");
    }

    public String getApiUrl() {
        return makeUrl("api");
    }

    public String getStatusUrl() {
        return makeUrl("status");
    }

    public String getAnalyzer() {
        return analyzer;
    }
}
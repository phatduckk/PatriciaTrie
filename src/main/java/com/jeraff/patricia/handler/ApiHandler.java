package com.jeraff.patricia.handler;

import com.google.gson.Gson;
import com.jeraff.patricia.conf.Config;
import com.jeraff.patricia.conf.Core;
import com.jeraff.patricia.util.Method;
import org.eclipse.jetty.server.Request;
import org.limewire.collection.PatriciaTrie;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ApiHandler extends BaseHandler {
    public static final String HEADER_PREFIX_COUNT = "X-Patricia-Prefix-Count";

    public ApiHandler(PatriciaTrie<String, String> patriciaTrie, Core core, Config config) {
        super(patriciaTrie, core, config);
    }

    public ApiMethodResult get(Params params, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final List<String> prefixedBy = patriciaTrieOps.getPrefixedBy(params.getFirstKey());
        return new ApiMethodResult(new HashMap<String, Object>(), prefixedBy);
    }

    public ApiMethodResult putPost(Params params, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final HashMap<String, ArrayList<String>> result = patriciaTrieOps.put(params.getStrings());
        return new ApiMethodResult(result);
    }

    public ApiMethodResult delete(Params params, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return new ApiMethodResult(patriciaTrieOps.remove(params.getStrings()));
    }

    public void head(Params params, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String firstKey = params.getFirstKey();
        int count;

        if (firstKey == null) {
            count = patriciaTrieOps.size();
        } else {
            count = patriciaTrieOps.getPrefixedByCount(firstKey);
        }

        if (count == 0 && firstKey != null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        response.setHeader(HEADER_PREFIX_COUNT, String.valueOf(count));
    }

    private void write(HttpServletResponse response, ApiMethodResult methodResult) throws IOException {
        write(response, methodResult.headers, methodResult.result);
    }

    private void write(HttpServletResponse response, Object o) throws IOException {
        write(response, null, o);
    }

    private void write(HttpServletResponse response, HashMap<String, Object> headers, Object o) throws IOException {
        final GZIPResponseWrapper wrapper = new GZIPResponseWrapper(response);

        if (o != null) {
            final Gson gson = new Gson();
            final String json = gson.toJson(o);
            wrapper.setContentLength(json.getBytes().length);
            wrapper.getWriter().write(json);
        }

        if (headers != null) {
            for (Map.Entry<String, Object> header : headers.entrySet()) {
                wrapper.addHeader(header.getKey(), header.getValue().toString());
            }
        }

        wrapper.setContentType("application/json");
        wrapper.setCharacterEncoding("UTF-8");
        wrapper.getWriter().close();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException, ServletException {
        final Params params = new Params(httpServletRequest);
        final Method method = Method.valueOf(baseRequest.getMethod());

        try {
            params.validate(method);
        } catch (ParamValidationError validationError) {
            handleValidationError(validationError, response);
            baseRequest.setHandled(true);
            return;
        }

        switch (method) {
            case GET:
                write(response, get(params, httpServletRequest, response));
                break;
            case DELETE:
                write(response, delete(params, httpServletRequest, response));
                break;
            case HEAD:
                head(params, httpServletRequest, response);
                break;
            default:
                write(response, putPost(params, httpServletRequest, response));
                break;
        }

        baseRequest.setHandled(true);
    }

    public void handleValidationError(ParamValidationError validationError, HttpServletResponse response) throws IOException {
        response.setStatus(validationError.code);
        write(response, validationError.getErrorMap());
    }

}

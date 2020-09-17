/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.fetcher.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.utils.UUID;
import io.gravitee.fetcher.api.FetcherConfiguration;
import io.gravitee.fetcher.api.FetcherException;
import io.gravitee.fetcher.api.FilesFetcher;
import io.gravitee.fetcher.api.Resource;
import io.gravitee.fetcher.gitlab.vertx.VertxCompletableFuture;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.utils.NodeUtils;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronSequenceGenerator;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitlabFetcher implements FilesFetcher {

    private static final Logger logger = LoggerFactory.getLogger(GitlabFetcher.class);

    private static final String HTTPS_SCHEME = "https";

    private GitlabFetcherConfiguration gitlabFetcherConfiguration;

    @Autowired
    private Vertx vertx;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private Node node;

    @Value("${httpClient.timeout:10000}")
    private int httpClientTimeout;
    @Value("${httpClient.proxy.type:HTTP}")
    private String httpClientProxyType;

    @Value("${httpClient.proxy.http.host:#{systemProperties['http.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpHost;
    @Value("${httpClient.proxy.http.port:#{systemProperties['http.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpPort;
    @Value("${httpClient.proxy.http.username:#{null}}")
    private String httpClientProxyHttpUsername;
    @Value("${httpClient.proxy.http.password:#{null}}")
    private String httpClientProxyHttpPassword;

    @Value("${httpClient.proxy.https.host:#{systemProperties['https.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpsHost;
    @Value("${httpClient.proxy.https.port:#{systemProperties['https.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpsPort;
    @Value("${httpClient.proxy.https.username:#{null}}")
    private String httpClientProxyHttpsUsername;
    @Value("${httpClient.proxy.https.password:#{null}}")
    private String httpClientProxyHttpsPassword;

    public GitlabFetcher(GitlabFetcherConfiguration gitlabFetcherConfiguration) {
        this.gitlabFetcherConfiguration = gitlabFetcherConfiguration;
    }

    @Override
    public FetcherConfiguration getConfiguration() {
        return this.gitlabFetcherConfiguration;
    }

    @Override
    public Resource fetch() throws FetcherException {
        checkRequiredFields(true);
        JsonNode jsonNode = this.request(getFetchUrl());

        final Resource resource = new Resource();
        if (jsonNode != null) {
            final Map<String, Object> metadata = mapper.convertValue(jsonNode, Map.class);
            final Object content = metadata.remove("content");
            if (content != null) {
                byte[] decodedContent = Base64.getDecoder().decode(String.valueOf(content));
                resource.setContent(new ByteArrayInputStream(decodedContent));
            }
            metadata.put(EDIT_URL_PROPERTY_KEY, buildEditUrl());
            metadata.put(PROVIDER_NAME_PROPERTY_KEY, "GitLab");
            resource.setMetadata(metadata);
        }
        return resource;
    }

    @Override
    public String[] files() throws FetcherException {
        checkRequiredFields(false);
        if ((gitlabFetcherConfiguration.getFilepath() == null || gitlabFetcherConfiguration.getFilepath().isEmpty())) {
            gitlabFetcherConfiguration.setFilepath("/");
        }
        JsonNode jsonNode = this.request(getTreeUrl());
        List<String> result = new ArrayList<>();
        if (jsonNode != null && jsonNode.isArray()) {
            ArrayNode tree = (ArrayNode) jsonNode;
            Iterator<JsonNode> elements = tree.elements();
            while (elements.hasNext()) {
                JsonNode elt = elements.next();
                String type = elt.get("type").asText();
                String path = elt.get("path").asText();
                if ("blob".equals(type)) {
                    result.add("/" + path);
                }
            }
        }
        return result.toArray(new String[0]);
    }

    private String buildEditUrl() throws FetcherException {
        checkRequiredFields(true);
        final String gitlabUrl = gitlabFetcherConfiguration.getGitlabUrl().replace("api/", "");
        return gitlabUrl.substring(0, gitlabUrl.lastIndexOf('/'))
                + '/' + gitlabFetcherConfiguration.getNamespace()
                + '/' + gitlabFetcherConfiguration.getProject()
                + "/edit/" + (gitlabFetcherConfiguration.getBranchOrTag() == null ?
                "master":gitlabFetcherConfiguration.getBranchOrTag())
                + '/' + gitlabFetcherConfiguration.getFilepath();
    }

    private void checkRequiredFields(boolean checkFilepath) throws FetcherException {
        if (gitlabFetcherConfiguration.getBranchOrTag() == null || gitlabFetcherConfiguration.getBranchOrTag().isEmpty()
                || gitlabFetcherConfiguration.getGitlabUrl() == null || gitlabFetcherConfiguration.getGitlabUrl().isEmpty()
                || (checkFilepath && (gitlabFetcherConfiguration.getFilepath() == null || gitlabFetcherConfiguration.getFilepath().isEmpty()))
                || gitlabFetcherConfiguration.getNamespace() == null || gitlabFetcherConfiguration.getNamespace().isEmpty()
                || gitlabFetcherConfiguration.getProject() == null || gitlabFetcherConfiguration.getProject().isEmpty()
                || (gitlabFetcherConfiguration.isAutoFetch() && (gitlabFetcherConfiguration.getFetchCron() == null || gitlabFetcherConfiguration.getFetchCron().isEmpty()))
        ) {
            throw new FetcherException("Some required configuration attributes are missing.", null);
        }

        if (gitlabFetcherConfiguration.isAutoFetch() && gitlabFetcherConfiguration.getFetchCron() != null) {
            try {
                new CronSequenceGenerator(gitlabFetcherConfiguration.getFetchCron());
            } catch (IllegalArgumentException e) {
                throw new FetcherException("Cron expression is invalid", e);
            }
        }
    }

    private String getFetchUrl() throws FetcherException {
        String ref = ((gitlabFetcherConfiguration.getBranchOrTag() == null || gitlabFetcherConfiguration.getBranchOrTag().trim().isEmpty())
                ? "master"
                : gitlabFetcherConfiguration.getBranchOrTag().trim());

        try {
            String encodedProject = URLEncoder.encode(gitlabFetcherConfiguration.getNamespace().trim() + '/' + gitlabFetcherConfiguration.getProject().trim(), "UTF-8");

            switch (gitlabFetcherConfiguration.getApiVersion()) {
                case V4:
                    String filepath = gitlabFetcherConfiguration.getFilepath().trim();
                    if (filepath.startsWith("/")) {
                        filepath = filepath.substring(1);
                    }
                    return gitlabFetcherConfiguration.getGitlabUrl().trim()
                            + "/projects/" + encodedProject
                            + "/repository/files/" + URLEncoder.encode(filepath, "UTF-8")
                            + "?ref=" + ref;
                default:
                    return gitlabFetcherConfiguration.getGitlabUrl().trim()
                            + "/projects/" + encodedProject
                            + "/repository/files"
                            + "?file_path=" + gitlabFetcherConfiguration.getFilepath().trim()
                            + "&ref=" + ref;
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Error thrown when trying to encode the url", e);
            throw new FetcherException("Error thrown when trying to encode the url", e);
        }
    }

    private String getTreeUrl() throws FetcherException {
        String ref = ((gitlabFetcherConfiguration.getBranchOrTag() == null || gitlabFetcherConfiguration.getBranchOrTag().trim().isEmpty())
                ? "master"
                : gitlabFetcherConfiguration.getBranchOrTag().trim());

        try {
            String encodedProject = URLEncoder.encode(gitlabFetcherConfiguration.getNamespace().trim() + '/' + gitlabFetcherConfiguration.getProject().trim(), "UTF-8");
            String filepath = gitlabFetcherConfiguration.getFilepath().trim();

            if (filepath.startsWith("/")) {
                if (filepath.length() == 1) {
                    filepath = "";
                } else {
                    filepath = filepath.substring(1);
                }
            }

            return gitlabFetcherConfiguration.getGitlabUrl().trim()
                    + "/projects/" + encodedProject
                    + "/repository/tree"
                    + "?path=" + URLEncoder.encode(filepath, "UTF-8")
                    + "&ref=" + ref
                    + "&recursive=true"
                    + "&per_page=100";
        } catch (UnsupportedEncodingException e) {
            logger.error("Error thrown when trying to encode the url", e);
            throw new FetcherException("Error thrown when trying to encode the url", e);
        }
    }

    private JsonNode request(String url) throws FetcherException {
        try {
            Buffer buffer = fetchContent(url).join();
            if (buffer == null || buffer.length() == 0) {
                logger.warn("Something goes wrong, Gitlab responds with a status 200 but the content is empty.");
                return null;
            }

            return new ObjectMapper().readTree(buffer.getBytes());
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            throw new FetcherException("Unable to fetch Gitlab content (" + ex.getMessage() + ")", ex);
        }
    }

    private CompletableFuture<Buffer> fetchContent(String url) throws Exception {
        CompletableFuture<Buffer> future = new VertxCompletableFuture<>(vertx);

        URI requestUri = URI.create(url);
        boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(requestUri.getScheme());

        final HttpClientOptions options = new HttpClientOptions()
                .setSsl(ssl)
                .setTrustAll(true)
                .setMaxPoolSize(1)
                .setKeepAlive(false)
                .setTcpKeepAlive(false)
                .setConnectTimeout(httpClientTimeout);

        if (gitlabFetcherConfiguration.isUseSystemProxy()) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setType(ProxyType.valueOf(httpClientProxyType));
            if (HTTPS_SCHEME.equals(requestUri.getScheme())) {
                proxyOptions.setHost(httpClientProxyHttpsHost);
                proxyOptions.setPort(httpClientProxyHttpsPort);
                proxyOptions.setUsername(httpClientProxyHttpsUsername);
                proxyOptions.setPassword(httpClientProxyHttpsPassword);
            } else {
                proxyOptions.setHost(httpClientProxyHttpHost);
                proxyOptions.setPort(httpClientProxyHttpPort);
                proxyOptions.setUsername(httpClientProxyHttpUsername);
                proxyOptions.setPassword(httpClientProxyHttpPassword);
            }
            options.setProxyOptions(proxyOptions);
        }

        final HttpClient httpClient = vertx.createHttpClient(options);

        httpClient.redirectHandler(resp -> {
            try {
                int statusCode = resp.statusCode();
                String location = resp.getHeader(HttpHeaders.LOCATION);
                if (location != null && (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307
                        || statusCode == 308)) {
                    HttpMethod m = resp.request().method();
                    if (statusCode == 301 || statusCode == 302 || statusCode == 303) {
                        m = HttpMethod.GET;
                    }
                    URI uri = HttpUtils.resolveURIReference(resp.request().absoluteURI(), location);
                    boolean redirectSsl;
                    int port = uri.getPort();
                    String protocol = uri.getScheme();
                    char chend = protocol.charAt(protocol.length() - 1);
                    if (chend == 'p') {
                        redirectSsl = false;
                        if (port == -1) {
                            port = 80;
                        }
                    } else if (chend == 's') {
                        redirectSsl = true;
                        if (port == -1) {
                            port = 443;
                        }
                    } else {
                        return null;
                    }
                    String requestURI = uri.getPath();
                    if (uri.getQuery() != null) {
                        requestURI += "?" + uri.getQuery();
                    }

                    RequestOptions requestOptions = new RequestOptions()
                            .setHost(uri.getHost())
                            .setPort(port)
                            .setSsl(redirectSsl)
                            .setURI(requestURI);

                    return Future.succeededFuture(httpClient.request(m, requestOptions));
                }
                return null;
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });

        final int port = requestUri.getPort() != -1 ? requestUri.getPort() :
                (HTTPS_SCHEME.equals(requestUri.getScheme()) ? 443 : 80);

        try {
            HttpClientRequest request = httpClient.request(
                    HttpMethod.GET,
                    port,
                    requestUri.getHost(),
                    requestUri.toString()
            );
            request.putHeader(io.gravitee.common.http.HttpHeaders.USER_AGENT, NodeUtils.userAgent(node));
            request.putHeader("X-Gravitee-Request-Id", io.gravitee.common.utils.UUID.toString(UUID.random()));

            // Follow redirect since Gitlab may return a 3xx status code
            request.setFollowRedirects(true);

            request.setTimeout(httpClientTimeout);

            if (gitlabFetcherConfiguration.getPrivateToken() != null && !gitlabFetcherConfiguration.getPrivateToken().trim().isEmpty()) {
                // Set GitLab token header
                request.putHeader("PRIVATE-TOKEN", gitlabFetcherConfiguration.getPrivateToken());
            }

            request.handler(response -> {
                if (response.statusCode() == HttpStatusCode.OK_200) {
                    response.bodyHandler(buffer -> {
                        future.complete(buffer);

                        // Close client
                        httpClient.close();
                    });
                } else {
                    future.completeExceptionally(new FetcherException("Unable to fetch '" + url + "'. Status code: " + response.statusCode() + ". Message: " + response.statusMessage(), null));

                    // Close client
                    httpClient.close();
                }
            });

            request.exceptionHandler(event -> {
                try {
                    future.completeExceptionally(event);

                    // Close client
                    httpClient.close();
                } catch (IllegalStateException ise) {
                    // Do not take care about exception when closing client
                }
            });

            request.end();
        } catch (Exception ex) {
            logger.error("Unable to fetch content using HTTP", ex);
            future.completeExceptionally(ex);
        }

        return future;
    }
}

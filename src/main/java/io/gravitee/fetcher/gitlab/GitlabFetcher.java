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
import io.gravitee.fetcher.api.Fetcher;
import io.gravitee.fetcher.api.FetcherException;
import org.asynchttpclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.util.Base64;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitlabFetcher implements Fetcher{

    private final Logger LOGGER = LoggerFactory.getLogger(GitlabFetcher.class);
    private GitlabFetcherConfiguration gitlabFetcherConfiguration;
    private AsyncHttpClient asyncHttpClient;
    private static final int GLOBAL_TIMEOUT = 10_000;

    public GitlabFetcher(GitlabFetcherConfiguration gitlabFetcherConfiguration) {
        this.gitlabFetcherConfiguration = gitlabFetcherConfiguration;
        this.asyncHttpClient = new DefaultAsyncHttpClient(
                new DefaultAsyncHttpClientConfig.Builder()
                        .setConnectTimeout(GLOBAL_TIMEOUT)
                        .setReadTimeout(GLOBAL_TIMEOUT)
                        .setRequestTimeout(GLOBAL_TIMEOUT)
                        .setMaxConnections(10)
                        .setMaxConnectionsPerHost(5)
                        .setAcceptAnyCertificate(true)
                        .build());
    }

    @Override
    public InputStream fetch() throws FetcherException {

        try {
            String url = gitlabFetcherConfiguration.getGitlabUrl().trim()
                    + "/projects/"
                    + URLEncoder.encode(gitlabFetcherConfiguration.getNamespace().trim() + "/" + gitlabFetcherConfiguration.getProject().trim(), "UTF-8")
                    + "/repository/files?file_path="
                    + gitlabFetcherConfiguration.getFilepath().trim()
                    + "&ref="
                    + ((gitlabFetcherConfiguration.getBranchOrTag() == null || gitlabFetcherConfiguration.getBranchOrTag().trim().isEmpty())
                      ? "master"
                      : gitlabFetcherConfiguration.getBranchOrTag().trim());

            Response response = this.asyncHttpClient
                    .prepareGet(url)
                    .addHeader("PRIVATE-TOKEN", gitlabFetcherConfiguration.getPrivateToken())
                    .execute()
                    .get();

            if (response.getStatusCode() != 200) {
                throw new FetcherException("Unable to fetch '" + url + "'. Status code: " + response.getStatusCode() + ". Message: " + response.getResponseBody(), null);
            }

            InputStream responseBodyAsStream = response.getResponseBodyAsStream();
            if(responseBodyAsStream.available() != 0) {
                JsonNode jsonNode = new ObjectMapper().readTree(responseBodyAsStream);
                if (jsonNode != null) {
                    JsonNode content = jsonNode.get("content");
                    if (content != null) {
                        String contentAsBase64 = content.asText();
                        byte[] decodedContent = Base64.getDecoder().decode(contentAsBase64);
                        return new ByteArrayInputStream(decodedContent);
                    }
                }
            }
            LOGGER.warn("Something goes wrong, Gitlab responds with a status 200 but the content is null.");
            return null;

        } catch (Exception e) {
            throw new FetcherException("Unable to fetch", e);
        }
    }
}

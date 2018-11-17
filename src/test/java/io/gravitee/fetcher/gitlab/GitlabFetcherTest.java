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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */

public class GitlabFetcherTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Test
    public void shouldNotFetchWithoutContent() throws FetcherException {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"key\": \"value\"}")));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        GitlabFetcher fetcher = new GitlabFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        InputStream fetch = fetcher.fetch();

        assertThat(fetch).isNull();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        GitlabFetcher fetcher = new GitlabFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        InputStream fetch = fetcher.fetch();
        assertThat(fetch).isNull();
    }

    @Test(expected = Exception.class)
    public void shouldThrowExceptionIfContentNotBase64() throws Exception {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"content\": \"not base64 content\"}")));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        GitlabFetcher fetcher = new GitlabFetcher(config);
        fetcher.setVertx(Vertx.vertx());

        fetcher.fetch();

        fail("Decode non base64 content does not throw Exception");
    }

    @Test
    public void shouldFetchBase64Content() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"content\": \""+encoded+"\"}")));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        GitlabFetcher fetcher = new GitlabFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        InputStream fetch = fetcher.fetch();

        assertThat(fetch).isNotNull();
        int n = fetch.available();
        byte[] bytes = new byte[n];
        fetch.read(bytes, 0, n);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(content);
    }

    @Test
    public void shouldFetchBase64ContentInV4() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        stubFor(get(urlEqualTo("/api/v4/projects/namespace%2Fproject/repository/files/%2Fpath%2Fto%2Ffile?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"content\": \""+encoded+"\"}")));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v4");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V4);
        GitlabFetcher fetcher = new GitlabFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        InputStream fetch = fetcher.fetch();

        assertThat(fetch).isNotNull();
        int n = fetch.available();
        byte[] bytes = new byte[n];
        fetch.read(bytes, 0, n);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(content);
    }

    @Test(expected = FetcherException.class)
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\n" +
                                "  \"message\": \"401 Unauthorized\"\n" +
                                "}")));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        GitlabFetcher fetcher = new GitlabFetcher(config);
        fetcher.setVertx(Vertx.vertx());

        try {
            fetcher.fetch();
        } catch (FetcherException fe) {
            assertThat(fe.getMessage().contains("Status code: 401"));
            assertThat(fe.getMessage().contains("Message: 401 Unauthorized"));
            throw fe;
        }

        fail("Fetch response with status code != 200 does not throw Exception");
    }
}

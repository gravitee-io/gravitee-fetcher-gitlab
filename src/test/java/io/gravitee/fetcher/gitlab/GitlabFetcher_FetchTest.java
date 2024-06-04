/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.fetcher.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
class GitlabFetcher_FetchTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private GitlabFetcher fetcher = new GitlabFetcher(null);

    private Vertx vertx = Vertx.vertx();
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void init() {
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        ReflectionTestUtils.setField(fetcher, "mapper", mapper);
    }

    @Test
    public void shouldNotFetchWithoutContent() throws FetcherException {
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNull();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse().withStatus(200))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();
        assertThat(fetch).isNull();
    }

    @Test
    public void shouldThrowExceptionIfContentNotBase64() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"content\": \"not base64 content\"}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);

        assertThatThrownBy(fetcher::fetch).hasMessage("Illegal base64 character 20");
    }

    @Test
    public void shouldFetchBase64Content() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"content\": \"" + encoded + "\"}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();

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

        wiremock.stubFor(
            get(urlEqualTo("/api/v4/projects/namespace%2Fproject/repository/files/path%2Fto%2Ffile?ref=sha1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"content\": \"" + encoded + "\"}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v4");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V4);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNotNull();
        int n = fetch.available();
        byte[] bytes = new byte[n];
        fetch.read(bytes, 0, n);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(content);
    }

    @Test
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1"))
                .willReturn(aResponse().withStatus(401).withBody("{\n" + "  \"message\": \"401 Unauthorized\"\n" + "}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(FetcherException.class)
            .hasMessageContaining("Status code: 401")
            .hasMessageContaining("Message: Unauthorized");
    }
}

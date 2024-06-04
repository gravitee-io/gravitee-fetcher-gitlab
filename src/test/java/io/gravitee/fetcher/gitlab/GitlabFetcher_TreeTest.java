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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitlabFetcher_TreeTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final GitlabFetcher fetcher = new GitlabFetcher(null);

    private Vertx vertx = Vertx.vertx();
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void init() {
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        ReflectionTestUtils.setField(fetcher, "mapper", mapper);
    }

    @Test
    public void shouldNotTreeWithoutContent() throws FetcherException {
        wiremock.stubFor(
            get(
                urlEqualTo(
                    "/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"
                )
            )
                .willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wiremock.getPort() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        wiremock.stubFor(
            get(
                urlEqualTo(
                    "/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"
                )
            )
                .willReturn(aResponse().withStatus(200))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wiremock.getPort() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        wiremock.stubFor(
            get(
                urlEqualTo(
                    "/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"
                )
            )
                .willReturn(aResponse().withStatus(401).withBody("{\n" + "  \"message\": \"401 Unauthorized\"\n" + "}"))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wiremock.getPort() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        assertThatThrownBy(fetcher::files)
            .isInstanceOf(FetcherException.class)
            .hasMessageContaining("Status code: 401")
            .hasMessageContaining("Message: Unauthorized");
    }

    @Test
    public void shouldTree() throws Exception {
        wiremock.stubFor(
            get(
                urlEqualTo(
                    "/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"
                )
            )
                .willReturn(aResponse().withStatus(200).withFixedDelay(100).withBody(treeResponse))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wiremock.getPort() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertThat(tree).hasSize(2);
        assertThat(tree).contains("/path/to/filepath/swagger.yml", "/path/to/filepath/subdir/doc.md");
    }

    @Test
    public void shouldTreeWithEmptyPath() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=&ref=sha1&recursive=true&per_page=100"))
                .willReturn(aResponse().withStatus(200).withBody(treeResponse))
        );
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath(null);
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wiremock.getPort() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertThat(tree).hasSize(2);
        assertThat(tree).contains("/path/to/filepath/swagger.yml", "/path/to/filepath/subdir/doc.md");
    }

    private final String treeResponse =
        """
                    [
                        {
                            "id": "f15543ee98011810baba7886e443684ff34460bb",
                            "name": "subdir",
                            "type": "tree",
                            "path": "path/to/filepath/subdir",
                            "mode": "040000"
                        },
                        {
                            "id": "f15543ee98011810baba7886e443684ff34460bb",
                            "name": "subsubdir",
                            "type": "tree",
                            "path": "path/to/filepath/subdir/subsubdir",
                            "mode": "040000"
                        },
                        {
                            "id": "8fbc3cda5e3d58d102ab2661543e0769fd21ba5b",
                            "name": "swagger.yml",
                            "type": "blob",
                            "path": "path/to/filepath/swagger.yml",
                            "mode": "100644"
                        },
                        {
                            "id": "7ec4657417aae9959960d21046dac9d251ba569e",
                            "name": "doc.md",
                            "type": "blob",
                            "path": "path/to/filepath/subdir/doc.md",
                            "mode": "100644"
                        }
                    ]""";
}

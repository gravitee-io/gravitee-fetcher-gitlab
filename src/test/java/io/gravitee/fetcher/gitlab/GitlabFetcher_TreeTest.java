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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitlabFetcher_TreeTest {

    @ClassRule
    public static final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private GitlabFetcher fetcher = new GitlabFetcher(null);

    private Vertx vertx = Vertx.vertx();
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void init() {
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        ReflectionTestUtils.setField(fetcher, "mapper", mapper);
    }

    @Test
    public void shouldNotTreeWithoutContent() throws FetcherException {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"))
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
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"))
                .willReturn(aResponse()
                        .withStatus(200)));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test(expected = FetcherException.class)
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"))
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
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        try {
            fetcher.files();
        } catch (FetcherException fe) {
            assertThat(fe.getMessage().contains("Status code: 401"));
            assertThat(fe.getMessage().contains("Message: 401 Unauthorized"));
            throw fe;
        }

        fail("Fetch response with status code != 200 does not throw Exception");
    }

    @Test
    public void shouldTree() throws Exception {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(100)
                        .withBody(treeResponse)));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath("/path/to/file");
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertEquals("get 2 files", 2, tree.length);
        List<String> asList = Arrays.asList(tree);
        assertTrue("swagger.yml", asList.contains("/path/to/filepath/swagger.yml"));
        assertTrue("doc.md", asList.contains("/path/to/filepath/subdir/doc.md"));
    }

    @Test
    public void shouldTreeWithEmptyPath() throws Exception {
        stubFor(get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/tree?path=&ref=sha1&recursive=true&per_page=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(treeResponse)));
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath(null);
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl("http://localhost:" + wireMockRule.port() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        ReflectionTestUtils.setField(fetcher, "gitlabFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertEquals("get 2 files", 2, tree.length);
        List<String> asList = Arrays.asList(tree);
        assertTrue("swagger.yml", asList.contains("/path/to/filepath/swagger.yml"));
        assertTrue("doc.md", asList.contains("/path/to/filepath/subdir/doc.md"));
    }

    private String treeResponse = "[\n" +
            "    {\n" +
            "        \"id\": \"f15543ee98011810baba7886e443684ff34460bb\",\n" +
            "        \"name\": \"subdir\",\n" +
            "        \"type\": \"tree\",\n" +
            "        \"path\": \"path/to/filepath/subdir\",\n" +
            "        \"mode\": \"040000\"\n" +
            "    },\n" +
            "    {\n" +
            "        \"id\": \"f15543ee98011810baba7886e443684ff34460bb\",\n" +
            "        \"name\": \"subsubdir\",\n" +
            "        \"type\": \"tree\",\n" +
            "        \"path\": \"path/to/filepath/subdir/subsubdir\",\n" +
            "        \"mode\": \"040000\"\n" +
            "    },\n" +
            "    {\n" +
            "        \"id\": \"8fbc3cda5e3d58d102ab2661543e0769fd21ba5b\",\n" +
            "        \"name\": \"swagger.yml\",\n" +
            "        \"type\": \"blob\",\n" +
            "        \"path\": \"path/to/filepath/swagger.yml\",\n" +
            "        \"mode\": \"100644\"\n" +
            "    },\n" +
            "    {\n" +
            "        \"id\": \"7ec4657417aae9959960d21046dac9d251ba569e\",\n" +
            "        \"name\": \"doc.md\",\n" +
            "        \"type\": \"blob\",\n" +
            "        \"path\": \"path/to/filepath/subdir/doc.md\",\n" +
            "        \"mode\": \"100644\"\n" +
            "    }\n" +
            "]";
}

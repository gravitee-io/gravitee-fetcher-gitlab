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
import io.vertx.core.http.HttpClient;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetServer;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

class GitlabFetcher_HttpClientTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/path/to/file&ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"content\": \"b2s=\"}")
            )
        );
        wiremock.stubFor(
            get(
                urlEqualTo(
                    "/api/v3/projects/namespace%2Fproject/repository/tree?path=path%2Fto%2Ffile&ref=sha1&recursive=true&per_page=100"
                )
            ).willReturn(aResponse().withStatus(200).withBody("[]"))
        );
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/missing&ref=sha1")).willReturn(
                aResponse().withStatus(404)
            )
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void should_reuse_one_http_client_across_fetches() throws Exception {
        for (int i = 0; i < 10; i++) {
            fetcher("/path/to/file", 10_000).fetch();
            fetcher("/path/to/file", 10_000).files();
            assertThatThrownBy(fetcher("/missing", 10_000)::fetch).isInstanceOf(FetcherException.class);
        }

        assertThat(registeredHttpClients()).isEqualTo(1);
    }

    @Test
    void should_not_share_a_client_between_fetches_with_and_without_system_proxy() throws Exception {
        fetcher("/path/to/file", 10_000).fetch();

        GitlabFetcher proxied = fetcher("/path/to/file", 10_000);
        ((GitlabFetcherConfiguration) proxied.getConfiguration()).setUseSystemProxy(true);
        ReflectionTestUtils.setField(proxied, "httpClientProxyType", "HTTP");
        ReflectionTestUtils.setField(proxied, "httpClientProxyHttpHost", "localhost");
        // Nothing listens there: the fetch fails, but only after its client has been built.
        ReflectionTestUtils.setField(proxied, "httpClientProxyHttpPort", 1);
        assertThatThrownBy(proxied::fetch).isInstanceOf(FetcherException.class);

        assertThat(registeredHttpClients()).isEqualTo(2);
    }

    @Test
    void should_not_share_a_client_between_fetches_with_different_timeouts() throws Exception {
        fetcher("/path/to/file", 10_000).fetch();
        fetcher("/path/to/file", 5_000).fetch();

        assertThat(registeredHttpClients()).isEqualTo(2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void should_use_https_proxy_and_port_for_an_upper_case_https_scheme() throws Exception {
        CompletableFuture<String> proxyRequestLine = new CompletableFuture<>();
        NetServer httpsProxy = vertx
            .createNetServer()
            .connectHandler(socket ->
                socket.handler(buffer -> {
                    proxyRequestLine.complete(buffer.toString().lines().findFirst().orElse(""));
                    socket.close();
                })
            )
            .listen(0)
            .toCompletionStage()
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);

        GitlabFetcher fetcher = fetcher("/path/to/file", 5_000);
        GitlabFetcherConfiguration config = (GitlabFetcherConfiguration) fetcher.getConfiguration();
        config.setGitlabUrl("HTTPS://gravitee.invalid/api/v3");
        config.setUseSystemProxy(true);
        ReflectionTestUtils.setField(fetcher, "httpClientProxyType", "HTTP");
        ReflectionTestUtils.setField(fetcher, "httpClientProxyHttpHost", "localhost");
        // Nothing listens there: picking the HTTP proxy fails without reaching the HTTPS one.
        ReflectionTestUtils.setField(fetcher, "httpClientProxyHttpPort", 1);
        ReflectionTestUtils.setField(fetcher, "httpClientProxyHttpsHost", "localhost");
        ReflectionTestUtils.setField(fetcher, "httpClientProxyHttpsPort", httpsProxy.actualPort());

        assertThatThrownBy(fetcher::fetch).isInstanceOf(FetcherException.class);
        assertThat(proxyRequestLine.get(5, TimeUnit.SECONDS)).startsWith("CONNECT gravitee.invalid:443 ");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_release_the_connection_of_a_404_response_without_waiting_for_its_body() throws Exception {
        should_release_the_connection_of_a_non_200_response_without_waiting_for_its_body(404);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_release_the_connection_of_a_500_response_without_waiting_for_its_body() throws Exception {
        should_release_the_connection_of_a_non_200_response_without_waiting_for_its_body(500);
    }

    private void should_release_the_connection_of_a_non_200_response_without_waiting_for_its_body(int status) throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/api/v3/projects/namespace%2Fproject/repository/files?file_path=/slow-error&ref=sha1")).willReturn(
                aResponse().withStatus(status).withBody("x".repeat(200_000)).withChunkedDribbleDelay(100, 20_000)
            )
        );

        // Enough failures to take every connection of the pool if their bodies kept them busy.
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(fetcher("/slow-error", 3_000)::fetch).isInstanceOf(FetcherException.class);
        }

        assertThat(fetcher("/path/to/file", 3_000).fetch().getContent()).isNotNull();
    }

    /**
     * Vert.x keeps every client it creates in the private {@code children} map of its owner's close future, and
     * only releases them when that owner closes. Counting them is the only way to observe from a test that a
     * client is retained for the whole life of the node rather than released after the call that created it.
     */
    private long registeredHttpClients() throws Exception {
        Object closeFuture = ((VertxInternal) vertx).closeFuture();
        Field childrenField = closeFuture.getClass().getDeclaredField("children");
        childrenField.setAccessible(true);
        Map<?, ?> children = (Map<?, ?>) childrenField.get(closeFuture);
        return children == null ? 0 : children.keySet().stream().filter(HttpClient.class::isInstance).count();
    }

    private GitlabFetcher fetcher(String filepath, int timeoutMs) {
        GitlabFetcherConfiguration config = new GitlabFetcherConfiguration();
        config.setFilepath(filepath);
        config.setProject("project");
        config.setNamespace("namespace");
        config.setGitlabUrl(wiremock.baseUrl() + "/api/v3");
        config.setBranchOrTag("sha1");
        config.setPrivateToken("token");
        config.setApiVersion(ApiVersion.V3);
        GitlabFetcher fetcher = new GitlabFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", timeoutMs);
        ReflectionTestUtils.setField(fetcher, "mapper", new ObjectMapper());
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        return fetcher;
    }
}

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

import static io.gravitee.fetcher.gitlab.ApiVersion.V4;

import io.gravitee.fetcher.api.FetcherConfiguration;
import io.gravitee.fetcher.api.FilepathAwareFetcherConfiguration;
import io.gravitee.fetcher.api.Sensitive;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class GitlabFetcherConfiguration implements FetcherConfiguration, FilepathAwareFetcherConfiguration {

    private String gitlabUrl;
    private boolean useSystemProxy;
    private String namespace;
    private String project;
    private String branchOrTag;
    private String filepath;

    @Sensitive
    private String privateToken;

    private ApiVersion apiVersion = V4;
    private String editLink;

    private String fetchCron;

    private boolean autoFetch = false;
}

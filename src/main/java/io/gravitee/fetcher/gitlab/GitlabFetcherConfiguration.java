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

import static io.gravitee.fetcher.gitlab.ApiVersion.V4;

import io.gravitee.fetcher.api.FetcherConfiguration;
import io.gravitee.fetcher.api.FilepathAwareFetcherConfiguration;
import io.gravitee.fetcher.api.Sensitive;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
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

    @Override
    public String getFetchCron() {
        return fetchCron;
    }

    public void setFetchCron(String fetchCron) {
        this.fetchCron = fetchCron;
    }

    @Override
    public boolean isAutoFetch() {
        return autoFetch;
    }

    public void setAutoFetch(boolean autoFetch) {
        this.autoFetch = autoFetch;
    }

    public String getGitlabUrl() {
        return gitlabUrl;
    }

    public void setGitlabUrl(String gitlabUrl) {
        this.gitlabUrl = gitlabUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getBranchOrTag() {
        return branchOrTag;
    }

    public void setBranchOrTag(String branchOrTag) {
        this.branchOrTag = branchOrTag;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getPrivateToken() {
        return privateToken;
    }

    public void setPrivateToken(String privateToken) {
        this.privateToken = privateToken;
    }

    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }

    public String getEditLink() {
        return editLink;
    }

    public void setEditLink(String editLink) {
        this.editLink = editLink;
    }
}

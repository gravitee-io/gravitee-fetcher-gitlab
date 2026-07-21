## [3.0.1](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/3.0.0...3.0.1) (2026-07-21)


### Bug Fixes

* throw explicit resource not found error and harmonize filepath docs ([e49e54c](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/e49e54c95baada2e529bd0b30a0960eedf3637bc))

# [3.0.0](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/2.1.2...3.0.0) (2026-07-15)


### Bug Fixes

* fail fetch when the connection stalls while reading the response ([e0acfc7](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/e0acfc756c78203025fe4bb8516ffdd35f5cd69b))
* log fetch errors once and unwrap the async exception wrapper ([d458e7c](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/d458e7cbe8727205c76becf533e363df5c370a8c))
* restore interrupt status when fetch is interrupted ([811c8ea](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/811c8ea680bce4187a28a8220002daf1ad76f0b4))


### Features

* upgrade to vertx 5 ([6a8a995](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/6a8a995d161e8c972cf20dd688e11e46fda4474b))


### BREAKING CHANGES

* compiled against Vert.x 5 (gravitee-bom 9.x), requires an APIM runtime on Vert.x 5

## [2.1.2](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/2.1.1...2.1.2) (2026-02-20)


### Bug Fixes

* impossible to retrieve documentation from private gitlab ([6d100e8](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/6d100e88855db0fd6c65f549f066c5ca5d1d7d43))

## [2.1.1](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/2.1.0...2.1.1) (2024-09-11)


### Bug Fixes

* improve schema form ([bb6ff14](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/bb6ff14ee3b3a5684789268896bf7f1b8454981a))

# [2.1.0](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/2.0.1...2.1.0) (2024-09-03)


### Features

* improve fetchCron field ([01e900a](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/01e900ae62b877457106ebc4e0bf63d53e01aa39))

## [2.0.1](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/2.0.0...2.0.1) (2024-06-10)


### Bug Fixes

* use right scope for lib ([67a1516](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/67a15167d975fa30b393ff4a8ecc22a5c501c0c7))

# [2.0.0](https://github.com/gravitee-io/gravitee-fetcher-gitlab/compare/1.11.0...2.0.0) (2024-06-05)


### chore

* bump dependencies ([969ba8c](https://github.com/gravitee-io/gravitee-fetcher-gitlab/commit/969ba8c315ee0a7f539877f6e5e3d8ca27fcf78d))


### BREAKING CHANGES

* require JDK 17

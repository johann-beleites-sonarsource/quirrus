# Quirrus
Simple tool to query the Cirrus CI API for different tasks.

## Automatically extracting data from logs to compare them from one task to the next
*The examples given here will use peach, in theory you can use whatever cirrus repository you want.*

You need to decide which peachee branches you want to fetch the data from.

You can either run it directy with gradle:
```
CIRRUS_COOKIE="<valid cirrus cookie>" ./gradlew run --args="EXTRACT -r <repository id or name> -l <log file name> -x '<regex to extract data from log>' [branches to download data for]"
```

For `github/SonarSource/<repositoryName>` you can provide the repository name and Quirrus will resolve its ID. Alternatively, you can provide the repository ID directly. Please note that the repository ID is numeric. You can find it when looking at the repository settings, inside the URL (e.g. `https://cirrus-ci.com/settings/repository/<REPOSITORY_ID>`).

Example (querying the `dotnet` branch):
```
.\gradlew run --args="EXTRACT -r peachee-languages -l scanner_end.log --regex 'Found (?<data>.*)' dotnet"
```

Alternatively you can build it with gradle and run the jar file directly, of course.

Quirrus will fetch the logs for each branch in parallel (but won't try to fetch several branches' 
logs in parallel). This gives pretty decent performance and is necessary, since a single request 
for a log is quite slow.

By default, Quirrus will fetch the latest build for a branch. You can adjust that behaviour by passing e.g. `my-branch~2` as branch name then it will fetch the second-latest build for branch `my-branch`. In other words, passing `my-branch` as branch name is a shorthand for `my-branch~1`

You can also supply multiple regexes by specifying multiple `-x` arguments. Quirrus will download the logs once and apply all regexes to every log, printing the results for each regex without having to re-download the logs all the time. Note that *every regex needs to have a `data` class*, which will be used to extract the desired values from the first match in the log.

## Downloading log files
e.g. to download the last 100 build logs for sonar-java:
```
CIRRUS_COOKIE="<valid cirrus cookie>" ./gradlew run --args="LOGS -r 6321405351690240 -b master -k build -l build.log -n 100 /tmp/scan_logs"
```

## A note on Authentication
You need to authenticate to Cirrus CI. This tool currently supports token-based and cookie-based authentication. However, token-based authentication doesn't seem to work with user tokens.

**Suggestion: use cookie-based authentication**: you can copy+paste your cookies from the browser. For this, simply copy the `Cookie` request header content from a request made to `api.cirrus-ci.com` in your browser to retrieve the logs for a build. Cirrus-ci will make a request to `api.cirrus-ci.com` pretty much when loading any page, it will also make other requests, though, which don't have the desired `Cookie` header but a different (for us useless) one.

Also, until Cirrus CI user tokens will work, only set the `CIRRUS_COOKIE` environment variable. If you set both variables ( `CIRRUS_TOKEN` and `CIRRUS_COOKIE` ) it will always try to use the token.

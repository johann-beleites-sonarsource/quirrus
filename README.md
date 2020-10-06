# Quirrus
Simple tool to query the Cirrus CI API 

You need to decide which peachee branches you want to fetch the data from. We'll use `dotnet` and 
`dotnet-symbolic` for our example here.

You can either run it directy with gradle:
```
CIRRUS_COOKIE="<valid cirrus cookie>" ./gradlew run --args="-r <repository id> -l <log file name> -x '<regex to extract data from log>' [branches to download data for]"
```

Or run the jar by passing the branches as command line arguments directly.

Quirrus will fetch the logs for each branch in parallel (but won't try to fetch several branches' 
logs in parallel). This gives pretty decent performance and is necessary, since a single request 
for a log is quite slow.

By default, Quirrus will fetch the latest build for a branch. You can adjust that behaviour by passing e.g. `my-branch~2` as branch name then it will fetch the second-latest build for branch `my-branch`. In other words, passing `my-branch` as branch name is a shorthand for `my-branch~1`

You can also supply multiple regexes by specifying multiple -x arguments. Quirrus will download the logs once and apply all regexes to every log, printing the results for each regex without having to re-download the logs all the time.

## A note on Authentication
You need to authenticate to Cirrus CI. This tool currently supports token-based and cookie-based
authentication. If you don't have a/the valid token, you can copy+paste your cookies from the
browser (simply copy the `Cookie` request header content from a request made to cirrus-ci in your
browser). 

If you have a token, put it into the environment variable `CIRRUS_TOKEN`. If you don't, put your
cookie into the environment variable `CIRRUS_COOKIE`. If you set both variables it will always try
to use the token.

# Quirrus
Simple tool to query the Cirrus CI API 

You need to decide which peachee branches you want to fetch the data from. We'll use `dotnet` and 
`dotnet-symbolic` for our example here.

You can either run it directy with gradle:
```./gradlew run --args="dotnet dotnet-symbolic"```

Or run the jar by passing the branches as command line arguments directly.

Quirrus will fetch the logs for each branch in parallel (but won't try to fetch several branches' 
logs in parallel). This gives pretty decent performance and is necessary, since a single request 
for a log is quite slow.

## A note on Authentication
You need to authenticate to Cirrus CI. This tool currently supports token-based and cookie-based
authentication. If you don't have a/the valid token, you can copy+paste your cookies from the
browser (simply copy the `Cookie` request header content from a request made to cirrus-ci in your
browser). 

If you have a token, put it into the environment variable `CIRRUS_TOKEN`. If you don't, put your
cookie into the environment variable `CIRRUS_COOKIE`. If you set both variables it will always try
to use the token.

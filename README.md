# Twitter Heat

[![Build Status](https://travis-ci.org/maxxhuang/twitter-heat.svg?branch=master)](https://travis-ci.org/maxxhuang/twitter-heat)


Twitter Heat is a clustering application utilizing Akka, Akka Cluster, Akka Stream, Akka HTTP, and Twitter4J to collect Twitter messages of interest.

## Architecture

The main components of Twitter Heat are "Collector", "Tweet Stream Worker" and "API". The interaction among these components is illustrated as belows.
 ![](image/twitter-heat-component.png?raw=true)

## Deployment

Twitter Heat uses SBT as a building tool and for publishing docker images. Twitter Heat is configured so that Collector, Stream Worker, Tweet Stream and Stats Manager are deployed in a docker container while API (Akka HTTP) is in another.

![](image/twitter-heat-deployment.png?raw=true)

## Run Twitter Heat in Docker

Executing the commands in the terminal under the project directory,

> sbt clean docker:publishLocal

creates 2 docker images:

1. twitter-heat-collector
2. twitter-heat-api

To quickly start a cluster of two collectors and one api server, run the command

> docker-compose up

You can see from the standard output that 3 nodes are brought up with collector1 as the leader.

### Keys and Access Tokens for Twitter API
A set of keys and access tokens is required to invoked Twitter's APIs. You need to go to https://dev.twitter.com/ to acquire a set of keys and tokens. Twitter Heat Collector looks up environment variables to find the keys and tokens you specified.
 
 1. TWITTER_CONSUMER_KEY
 2. TWITTER_CONSUMER_SECRET
 3. TWITTER_ACCESS_TOKEN
 4. TWITTER_ACCESS_TOKEN_SECRET 

For Twitter Heat, you should enter your won Twitter keys/tokens in docker-compose.yml. 

## Twitter Heat API

After starting the Twitter Heat cluster using docker-compose, you can access the API server in port 8080 using any HTTP client.

### Start a Query
> curl -X POST localhost:8080/query/taiwan

"taiwan" is the keyword that you are interested in. Twitter Heat will create tweet stream gathering messages containing the keyword.

### Stop a Query
> curl -X DELETE localhost:8080/query/taiwan

Stop the tweet steam with the specified keyword. If no tweet streams match the keyword, a 404 HTTP status code is returned.

```
$ curl -I -X DELETE localhost:8080/query/galaxy
HTTP/1.1 404 Not Found
Server: akka-http/10.0.5
Date: Sat, 29 Apr 2017 08:07:56 GMT
Content-Length: 0
```

### List Running Queries
> curl -X GET localhost:8080/query

```
$ curl -X GET localhost:8080/query
["taiwan","japan"]
```

### Get Current Statistics
> curl -X GET localhost:8080/stats

```
$ curl -X GET localhost:8080/stats
{"japan":12094,"taiwan":584}
```

## Twitter API Rate Limiting
Twitter imposes the rate limiting on its public APIs. Twitter Heat make a Twitter login with every query request. If you start a query too often, you will see the Twitter rate limiting warning in the log. 

Of course, a Twitter login per query request is a flawed implementation. A login session can be shared by multiple query requests and hence multiple tweet stream.

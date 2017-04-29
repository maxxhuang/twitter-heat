package twitterheat.api

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import twitterheat.api.route.ApiRoute
import twitterheat.collector.{TweetCollector, TweetStatsManager}
import twitterheat.common.config.TwitterHeatConfig


object Main extends App {

  val clusterSystem = TwitterHeatConfig.clusterSystem()

  val collector = clusterSystem.actorOf(
    TweetCollector.singletonProxyProps(clusterSystem),
    TweetCollector.name)

  val statsManager = clusterSystem.actorOf(
    TweetStatsManager.singletonProxyProps(clusterSystem),
    TweetStatsManager.name
  )

  implicit val apiSystem = ActorSystem("twitter-heat-api")
  implicit val apiMaterializer = ActorMaterializer()

  val futureBinding = Http(apiSystem).bindAndHandle(ApiRoute(collector, statsManager), "0.0.0.0", 8080)

  implicit val ec = apiSystem.dispatcher
  futureBinding.foreach { binding =>
    val logger = Logging(apiSystem, "API HTTP Server")

    logger.info("API HTTP Server is bound to {}:{}",
      binding.localAddress.getHostName,
      binding.localAddress.getPort)
  }


}

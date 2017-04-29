package twitterheat.collector

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.FromConfig
import twitterheat.common.config.TwitterHeatConfig


object Main extends App {

  implicit val clusterSystem = TwitterHeatConfig.clusterSystem()

  val collectorConfig = TweetCollectorConfig.fromConfig()

  val (statsManager, statsProxy) = createStatsManager(clusterSystem)

  val workerPool = clusterSystem.actorOf(
    FromConfig.props(TweetStreamWorker.props(collectorConfig, statsProxy)),
    TweetStreamWorker.name
  )

  val collector = clusterSystem.actorOf(
    TweetCollector.singletonProps(collectorConfig, workerPool),
    TweetCollector.name)


  def createStatsManager(clusterSystem: ActorSystem): (ActorRef, ActorRef) = {
    val manager = clusterSystem.actorOf(
      TweetStatsManager.singletonProps(clusterSystem),
      TweetStatsManager.name
    )

    val proxy = clusterSystem.actorOf(
      TweetStatsManager.singletonProxyProps(clusterSystem),
      TweetStatsManager.proxyName
    )

    (manager, proxy)
  }

}

package twitterheat.collector

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import twitterheat.collector.TweetStatsManager.{GetStatistics, Increment, Incremented, Statistics}


object TweetStatsManager {

  val name = "statsManager"
  val proxyName = "statsProxy"
  val clusterRole = "statsManager"

  case object GetStatistics
  case class Statistics(summary: Map[String, Int])
  case class Increment(query: String)
  case class Incremented(query: String)


  def props: Props = Props[TweetStatsManager]

  def singletonProps(implicit clusterActorSystem: ActorSystem): Props =
    ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(clusterActorSystem).withRole(clusterRole)
    )

  def singletonProxyProps(implicit clusterActorSystem: ActorSystem): Props =
    ClusterSingletonProxy.props(
      singletonManagerPath = s"/user/$name",
      settings = ClusterSingletonProxySettings(clusterActorSystem).withRole(clusterRole))

}

class TweetStatsManager extends Actor with ActorLogging {

  val queryCountMap = collection.mutable.HashMap.empty[String, Int]

  override def receive: Receive = {
    case GetStatistics =>
      sender ! Statistics(queryCountMap.toMap)

    case Increment(query) =>
      log.info("Received incoming stats for {}", query)
      val count = queryCountMap.getOrElse(query, 0)
      queryCountMap += (query -> (count + 1))
      sender ! Incremented(query)
  }

}
package twitterheat.collector

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import twitterheat.collector.TweetCollector._
import twitterheat.collector.TweetStreamWorker.{InitTweetStream, StopTweetStream, TweetStreamInitialized, TweetStreamStopped}


object TweetCollector {

  val name = "collector"
  val clusterRole = "collector"

  case class StartTweetCollecting(query: String)
  case class StopTweetCollecting(query: String)
  case class TweetCollectingStarted(query: String)
  case class TweetCollectingStopped(query: String)
  case class NoTweetCollectingFouond(query: String)
  case object ListRunningQueries
  case class RunningQueries(queries: List[String])


  def props(collectorConfig: TweetCollectorConfig,
            tweetStreamWorkerPool: ActorRef): Props =
    Props(classOf[TweetCollector], collectorConfig, tweetStreamWorkerPool)

  def singletonProps(collectorConfig: TweetCollectorConfig,
                     tweetStreamWorkerPool: ActorRef)
                    (implicit clusterActorSystem: ActorSystem): Props =
    ClusterSingletonManager.props(
      singletonProps = TweetCollector.props(collectorConfig, tweetStreamWorkerPool),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(clusterActorSystem).withRole(clusterRole)
    )

  def singletonProxyProps(implicit clusterActorSystem: ActorSystem): Props =
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$name",
        settings = ClusterSingletonProxySettings(clusterActorSystem).withRole(clusterRole))

}

class TweetCollector(collectorConfig: TweetCollectorConfig,
                     tweetStreamWorkerPool: ActorRef) extends Actor with ActorLogging {

  var workerMap = Map.empty[String, ActorRef]

  var pendingStartQueryMap = Map.empty[String, Set[ActorRef]]
  var pendingStopQueryMap = Map.empty[String, Set[ActorRef]]


  override def receive: Receive =
    handleStartingQuery orElse
    handleStoppingQuery orElse
    handleListingQueries

  def handleStartingQuery: Receive = {
    case StartTweetCollecting(query) =>
      workerMap.get(query).fold
      { // no worker for this query
        val pendingRequesters = pendingStartQueryMap.get(query).getOrElse(Set.empty) + sender
          pendingStartQueryMap += (query -> pendingRequesters)

        tweetStreamWorkerPool ! InitTweetStream(query)
      }
      { // tweet stream has been started for this query
        _ => sender ! TweetCollectingStarted(query)
      }

    case TweetStreamInitialized(query, worker) =>
      context.watch(worker)
      workerMap += (query -> worker)
      pendingStartQueryMap.get(query).foreach(_.foreach(_ ! TweetCollectingStarted(query)))
      pendingStartQueryMap -= query
  }

  def handleStoppingQuery: Receive = {
    case StopTweetCollecting(query) =>
      workerMap.get(query).fold
      { // no worker for this query
        log.error("Failed to stop collecting tweets with {}. TweetCollector({}) is not collecting {}.",
          query, self.path.name, query)

        sender ! NoTweetCollectingFouond(query)
      }
      { // tweet stream ahs been started for this query
        worker =>
          log.info(s"Trying to stop tweet stream for query($query) from ${worker.path.name}")
          val pendingRequesters = pendingStopQueryMap.get(query).getOrElse(Set.empty) + sender
          pendingStopQueryMap += (query -> pendingRequesters)
          worker ! StopTweetStream(query)
      }

    case TweetStreamStopped(query) =>
      workerMap.get(query).fold
      { // no worker for this query
        log.error("{} does not recognize the terminated tweet stream for query({}).",
          self.path, query)
      }
      { // tweet stream ahs been started for this query
        worker =>
          log.info(s"Tweet Stream for qeury ($query) has been stopped.")
          workerMap -= query
          pendingStopQueryMap.get(query).foreach(_.foreach(_ ! TweetCollectingStopped(query)))
          pendingStopQueryMap -= query
      }

  }

  def handleListingQueries: Receive = {
    case ListRunningQueries =>
      sender ! RunningQueries(workerMap.keys.toList)
  }

}


package twitterheat.collector

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, OverflowStrategy}
import org.reactivestreams.Publisher
import twitter4j._
import twitter4j.conf.{Configuration, ConfigurationBuilder}
import twitterheat.collector.TweetStatsManager.Increment
import twitterheat.collector.TweetStreamWorker._
import twitterheat.common.config.TwitterHeatConfig


object TweetStreamWorker {

  val name = "workerPool"

  case class InitTweetStream(query: String)
  case class TweetStreamInitialized(query: String, worker: ActorRef)

  case class StopTweetStream(query: String)
  case class TweetStreamStopped(query: String)
  case class NoTweetStreamFound(query: String)

  // all twitter steams on the same node share one actor system
  private val twitterStreamActorSystem = ActorSystem("twitter-stream")

  def props(collectorConfig: TweetCollectorConfig, statsManager: ActorRef): Props =
    Props(classOf[TweetStreamWorker], collectorConfig, statsManager)

}

class TweetStreamWorker(collectorConfig: TweetCollectorConfig,
                        tweetStatsManager: ActorRef) extends Actor with ActorLogging {

  case class TwitterStreamControl(twitterStream: TwitterStream, killSwitch: KillSwitch)


  var queryControlMap = Map.empty[String, TwitterStreamControl]


  override def receive: Receive = {
    case InitTweetStream(query) =>
      val control = initStream(query)
      queryControlMap += (query -> control)
      sender ! TweetStreamInitialized(query, self)

    case StopTweetStream(query) =>
      queryControlMap.get(query).fold
      { // no worker for this query
        sender ! NoTweetStreamFound(query)
      }
      { // tweet stream ahs been started for this query
        control =>
          control.killSwitch.shutdown()
          control.twitterStream.shutdown()

          queryControlMap -= query
          sender ! TweetStreamStopped(query)
      }

  }

  private def initStream(query: String): TwitterStreamControl = {

    log.info("establish tweet stream\nconsumerKey={}\nconsumerSecret={}\naccessToken={}\naccessTokenSecret={}",
      collectorConfig.consumerKey, collectorConfig.consumerSecret,
      collectorConfig.accessToken, collectorConfig.accessTokenSecret)

    val twitterConfig: Configuration = new ConfigurationBuilder()
      .setDebugEnabled(true)
      .setOAuthConsumerKey(collectorConfig.consumerKey)
      .setOAuthConsumerSecret(collectorConfig.consumerSecret)
      .setOAuthAccessToken(collectorConfig.accessToken)
      .setOAuthAccessTokenSecret(collectorConfig.accessTokenSecret)
      .build()

    val twitterStream: TwitterStream = new TwitterStreamFactory(twitterConfig).getInstance()

    implicit val materializer = ActorMaterializer()(twitterStreamActorSystem)

    val killSwitch = KillSwitches.shared(s"${self.path.name}-$query")

    val (actorRef: ActorRef, publisher: Publisher[Tweet]) =
      Source.actorRef[Tweet](1000, OverflowStrategy.fail)
        .via(killSwitch.flow)
        .toMat(Sink.asPublisher(false))(Keep.both)
        .run()

    val statusListener = actorStatusListener(actorRef)

    twitterStream.addListener(statusListener)
    twitterStream.filter(new FilterQuery(0, Array(), Array(query)))

    import akka.pattern.ask

    implicit val defaultTimeout = TwitterHeatConfig.defaultTimeout

    // start streaming tweets
    Source
      .fromPublisher(publisher)
      .via(killSwitch.flow)
      .mapAsyncUnordered(5)(tweet => tweetStatsManager ? Increment(query)) // use "ask" instead of "tell" to honor back-pressure
      .runWith(Sink.ignore)

    TwitterStreamControl(twitterStream, killSwitch)

  }

  private def actorStatusListener(reportingActor: ActorRef): StatusListener = new StatusListener {

    override def onStatus(status: Status): Unit = {

      log.info("onStatus: {}", status)

      reportingActor ! Tweet(
        status.getUser.getId,
        status.getUser.getScreenName,
        status.getText,
        LocalDateTime.now())
    }

    override def onStallWarning(warning: StallWarning): Unit = {
      log.info("onStallWarning: {}", warning)
    }

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {
      log.info("onDeletionNotice: {}", statusDeletionNotice)
    }

    override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {
      log.info("onScrubGeo: userId({}), upToStatusId({})", userId, upToStatusId)
    }

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {
      log.info("onTrackLimitationNotice: {}", numberOfLimitedStatuses)
    }

    override def onException(ex: Exception): Unit = {
      log.info("onException: {}", ex.getMessage)
      ex.printStackTrace()
    }
  }

}


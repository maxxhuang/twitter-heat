package akkastreamexample

import java.time.LocalDateTime

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import akka.routing.{FromConfig, RoundRobinPool}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import org.reactivestreams.Publisher
import twitter4j._
import twitter4j.conf.{Configuration, ConfigurationBuilder}
import twitterheat.collector.TweetCollector.{StartTweetCollecting, StopTweetCollecting, TweetCollectingStarted, TweetCollectingStopped}
import twitterheat.collector.TweetStatsManager.Increment
import twitterheat.collector.TweetStreamWorker.InitTweetStream
import twitterheat.collector.{TweetCollector, TweetCollectorConfig, TweetStatsManager, TweetStreamWorker}
import twitterheat.common.config.TwitterHeatConfig

import scala.annotation.tailrec
import scala.io.StdIn


object ClusterTest extends App {

  val ac = ActorSystem("cluster-test")

  val twitterConfig = TweetCollectorConfig(
    "VL7dzmzEyM6F9851Vhx9HqxJg",
    "4YZ46mSUnMEbXVcCruoYQPeNVLAmflH4dua6Bi6LlY7ooDwfln",
    "27071990-t7tM91AOq0zabfBcQ0KzGK4svV1REWRz5P7Elyc63",
    "UduxenetUQodxyGPu0286712ecCpWnL0UXY9p38d1mYRa"
  )

  val stats = ac.actorOf(Props(new Actor {
    override def receive: Receive = {
      case msg @ Increment(query) =>
        println(msg)
        sender ! query
    }
  }))

  val worker = ac.actorOf(TweetStreamWorker.props(twitterConfig, stats))

  worker ! InitTweetStream("java")

//
//  val collector = ac.actorOf(
//    TweetCollector.props(twitterConfig),
//    "collector"
//  )


//  implicit val actorSystem = TwitterHeatConfig.clusterSystem()
//
//  val collectorConfig = TweetCollectorConfig.fromConfig()
//
//  val statsManager = actorSystem.actorOf(
//    TweetStatsManager.props
//  )
//
//  val worker = actorSystem.actorOf(
//    TweetStreamWorker.props(collectorConfig, statsManager)
//  )
//
//  val collector = actorSystem.actorOf(
//    FromConfig.props(TweetCollector.props(collectorConfig, worker)),
//    "collector"
//  )
//
//  val commander = actorSystem.actorOf(Props(classOf[CommandActor], collector), "commander")
//
//  commandLoop(commander)

  def commandLoop(commandActor: ActorRef): Unit = {

    object Command {
      def unapply(command: String): Option[(String, String)] = {
        val elems = command.split("[,\\s]+")
        if (elems.size == 2) Some(elems(0), elems(1))
        else None
      }
    }

    @tailrec
    def processCommand(): Unit = {
      StdIn.readLine("Input command:") match {
        case Command(action, query) => commandActor ! (action, query)
        case _ => println("invalid command")
      }

      processCommand()
    }

    processCommand()
  }

  class CommandActor(collector: ActorRef) extends Actor with ActorLogging {


    override def receive: Receive = {
      case ("query", query: String) =>
        collector ! StartTweetCollecting(query)

      case ("stop", query: String) =>
        collector ! StopTweetCollecting(query)

      case TweetCollectingStarted(query) =>
        println(s"Tweet Stream for $query has been started.")

      case TweetCollectingStopped(query) =>
        println(s"Tweet Stream for $query has been stopped.")
    }
  }

}

object RouterTest extends App {

  val ac = ActorSystem("router-test")
  val client = ac.actorOf(Props[Client], "client")

  class Client extends Actor with ActorLogging {

    val routee = context.actorOf(
      Props[Routee].withRouter(RoundRobinPool(nrOfInstances = 3)),
      "router"
    )

    routee ! "start"

    override def receive: Receive = {
      case ar: ActorRef =>
        log.info(s"invoked a routee(${ar.path})")
        ar ! "specific"

      case path: ActorPath =>
        log.info(s"Routee echoed its path: $path")

    }
  }

  class Routee extends Actor with ActorLogging {
    override def receive: Receive = {
      case "start" =>
        log.info(s"${self.path} is invoked")
        sender ! self

      case "specific" =>
        log.info(s"received a specific command from ${sender.path}")
        sender ! self.path
    }
  }

}

object TwitterTest extends App {

  object MessageType extends Enumeration {
    val Twitter, Facebook = Value
  }

  case class MatchedMessage(authorId: Any, authorName: String, text: String, messageType: MessageType.Value, timestamp: LocalDateTime)


//  val twitterConfig: Configuration = new ConfigurationBuilder()
//    .setDebugEnabled(true)
//    .setOAuthConsumerKey("PPzo38afuCmSnDt1366zt5450")
//    .setOAuthConsumerSecret("skthu43h4d8yr4FSeTxPsYgNlDnwFU9vNJ2qvxyWQlWCJrsTgr")
//    .setOAuthAccessToken("27071990-wEE4uydQHACx2B6K5Oy0aE6N3ijpb9FIjquiIIqtG")
//    .setOAuthAccessTokenSecret("Qd68Nh0BBJWR2qXz97LWDLtE5GxkJFz8v9y0n648KgT9Q")
//    .build()

  /*
  - "TWITTER_CONSUMER_KEY=VL7dzmzEyM6F9851Vhx9HqxJg"
      - "TWITTER_CONSUMER_SECRET=4YZ46mSUnMEbXVcCruoYQPeNVLAmflH4dua6Bi6LlY7ooDwfln"
      - "TWITTER_ACCESS_TOKEN=27071990-t7tM91AOq0zabfBcQ0KzGK4svV1REWRz5P7Elyc63"
      - "TWITTER_ACCESS_TOKEN_SECRET=UduxenetUQodxyGPu0286712ecCpWnL0UXY9p38d1mYRa"
   */
  val twitterConfig: Configuration = new ConfigurationBuilder()
    .setDebugEnabled(true)
    .setOAuthConsumerKey("VL7dzmzEyM6F9851Vhx9HqxJg")
    .setOAuthConsumerSecret("4YZ46mSUnMEbXVcCruoYQPeNVLAmflH4dua6Bi6LlY7ooDwfln")
    .setOAuthAccessToken("27071990-t7tM91AOq0zabfBcQ0KzGK4svV1REWRz5P7Elyc63")
    .setOAuthAccessTokenSecret("UduxenetUQodxyGPu0286712ecCpWnL0UXY9p38d1mYRa")
    .build()

//  val twitterConfig: Configuration = new ConfigurationBuilder()
//    .setDebugEnabled(true)
//    .setOAuthConsumerKey("TqcY6sAc4nqP5LNuOMn0nxICG")
//    .setOAuthConsumerSecret("ejiMlulWDLcqnJRkkcvDNbNc7mZPiVmAJOIWyjSzEYkRUg2K0d")
//    .setOAuthAccessToken("27071990-Gdqe4cNoEctILXZRiRcxu6ug9VdipOu61fgcaqXky")
//    .setOAuthAccessTokenSecret("IteOPvqWCDCsFWkwt99x4e5qDYLzEPIHPusuFtyACHgxu")
//    .build()

  val twitterStream: TwitterStream = new TwitterStreamFactory(twitterConfig).getInstance()


  val actorSystem = ActorSystem("twittertest")
  implicit val materializer = ActorMaterializer()(actorSystem)


  val (actorRef: ActorRef, publisher: Publisher[MatchedMessage]) = Source.actorRef[MatchedMessage](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(false))(Keep.both)
    .run()

  val statusListener = actorStatusListener(actorRef)

  twitterStream.addListener(statusListener)
  twitterStream.filter(new FilterQuery(0, Array(), Array("scala", "f#", "functional language", "monad", "flatMap", "akka")))

  Source.fromPublisher(publisher).runForeach(println)

  def printStatusListener(): StatusListener = new StatusListener {

    override def onStatus(status: Status): Unit = {
      println(s"onStatus: $status")
    }

    override def onStallWarning(warning: StallWarning): Unit = {
      println(s"onStallWarning: $warning")
    }

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {
      println(s"onDeletionNotice: $statusDeletionNotice")
    }

    override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {
      println(s"onScrubGeo: userId($userId), upToStatusId($upToStatusId)")
    }

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {
      println(s"onTrackLimitationNotice: $numberOfLimitedStatuses")
    }

    override def onException(ex: Exception): Unit = {
      println(s"onException: $ex")
    }
  }

  def actorStatusListener(reportingActor: ActorRef): StatusListener = new StatusListener {

    override def onStatus(status: Status): Unit = {

      println(s"onStatus: $status")

      reportingActor ! MatchedMessage(
        status.getUser.getId,
        status.getUser.getScreenName,
        status.getText,
        MessageType.Twitter,
        LocalDateTime.now())
    }

    override def onStallWarning(warning: StallWarning): Unit = {
      println(s"onStallWarning: $warning")
    }

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {
      println(s"onDeletionNotice: $statusDeletionNotice")
    }

    override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {
      println(s"onScrubGeo: userId($userId), upToStatusId($upToStatusId)")
    }

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {
      println(s"onTrackLimitationNotice: $numberOfLimitedStatuses")
    }

    override def onException(ex: Exception): Unit = {
      println(s"onException: $ex")
    }
  }


}

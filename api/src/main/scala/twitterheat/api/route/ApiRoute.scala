package twitterheat.api.route

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import spray.json.DefaultJsonProtocol
import twitterheat.collector.TweetCollector._
import twitterheat.collector.TweetStatsManager.{GetStatistics, Statistics}
import twitterheat.common.config.TwitterHeatConfig

import scala.util.{Failure, Success}

object ApiRoute extends Directives with DefaultJsonProtocol with SprayJsonSupport {

  import akka.pattern.ask
  implicit val defaultTimeout = TwitterHeatConfig.defaultTimeout

  def apply(collector: ActorRef, statsManager: ActorRef): Route =
    queryRoute(collector) ~ statsRoute(statsManager)

  def queryRoute(collector: ActorRef): Route = pathPrefix("query") {
    pathEndOrSingleSlash {
      val future = collector ? ListRunningQueries

      onComplete(future) {
        case Success(RunningQueries(queries)) => complete(queries)
        case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
      }
    } ~
    path(Segment) { query =>
      post {

        val future = collector ? StartTweetCollecting(query)

        onComplete(future) {
          case Success(TweetCollectingStarted(query)) => complete(HttpResponse(StatusCodes.OK))
          case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
        }

      } ~
      delete {

        val future = collector ? StopTweetCollecting(query)

        onComplete(future) {
          case Success(TweetCollectingStopped(query)) => complete(HttpResponse(StatusCodes.OK))
          case Success(NoTweetCollectingFouond(query)) => complete(HttpResponse(StatusCodes.NotFound))
          case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
        }

      }
    }

  }

  def statsRoute(statsManager: ActorRef): Route = (path(("stats")) & get) {

    val future = statsManager ? GetStatistics

    onComplete(future) {
      case Success(Statistics(summary)) => complete(summary)
      case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
    }

  }

}

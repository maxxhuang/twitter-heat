package twitterheat.common.config

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._


object TwitterHeatConfig {

  private lazy val defaultConfig: Config = ConfigFactory.load().resolve().getConfig("twitter-heat")

  def apply(): Config = defaultConfig

  lazy val defaultTimeoutInSecond: Int = defaultConfig.getInt("default-timeout-in-second")

  lazy val defaultTimeout: Timeout = defaultTimeoutInSecond seconds

  def clusterSystem(): ActorSystem = ActorSystem("twitter-heat", defaultConfig)

}

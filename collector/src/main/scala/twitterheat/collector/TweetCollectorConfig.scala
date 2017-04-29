package twitterheat.collector

import com.typesafe.config.Config
import twitterheat.common.config.TwitterHeatConfig


case class TweetCollectorConfig(
  consumerKey: String,
  consumerSecret: String,
  accessToken: String,
  accessTokenSecret: String
)

object TweetCollectorConfig {

  def fromConfig(config: Config = TwitterHeatConfig(),
                 keyPrefix: String = "twitter-keys",
                 key: String = "default"): TweetCollectorConfig = {

    val keyConfig = config.getConfig(s"$keyPrefix.$key")

    TweetCollectorConfig(
      keyConfig.getString("consumer-key"),
      keyConfig.getString("consumer-secret"),
      keyConfig.getString("access-token"),
      keyConfig.getString("access-token-secret")
    )
  }

}
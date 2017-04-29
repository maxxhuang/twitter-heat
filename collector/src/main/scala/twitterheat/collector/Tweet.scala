package twitterheat.collector

import java.time.LocalDateTime


case class Tweet(id: Any, screenName: String, text: String, timestamp: LocalDateTime)

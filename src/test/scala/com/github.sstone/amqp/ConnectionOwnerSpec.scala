package com.github.sstone.amqp

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.WordSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.actor.ActorSystem
import akka.pattern.gracefulStop
import akka.util.Timeout
import concurrent.duration._
import concurrent.Await
import com.rabbitmq.client.{ConnectionFactory, Address, Channel}
import ConnectionOwner.CreateChannel
import java.util.concurrent.TimeUnit

@RunWith(classOf[JUnitRunner])
class ConnectionOwnerSpec extends TestKit(ActorSystem("TestSystem")) with WordSpec with ShouldMatchers with ImplicitSender {
  implicit val timeout = Timeout(5 seconds)

  "ConnectionOwner" should {
    "provide channels for many child actors" in {
      val connFactory = new ConnectionFactory()
      val uri = system.settings.config.getString("amqp-client-test.rabbitmq.uri")
      connFactory.setUri(uri)
      val conn = system.actorOf(ConnectionOwner.props(connFactory))
      Amqp.waitForConnection(system, conn).await(2, TimeUnit.SECONDS)
      val actors = 100
      for (i <- 0 until actors) {
        val p = TestProbe()
        p.send(conn, CreateChannel)
        p.expectMsgClass(2.second, classOf[Channel])
      }
      Await.result(gracefulStop(conn, 5 seconds), 6 seconds)
    }
    "connect even if the default host is unavailable" in {
      val connFactory = new ConnectionFactory()
      val uri = system.settings.config.getString("amqp-client-test.rabbitmq.uri")
      connFactory.setUri(uri)
      val goodHost = connFactory.getHost
      connFactory.setHost("fake-host")
      val conn = system.actorOf(ConnectionOwner.props(connFactory, addresses = Some(Array(
          new Address("another.fake.host"),
          new Address(goodHost)
        ))))
      Amqp.waitForConnection(system, conn).await(50, TimeUnit.SECONDS)
      val actors = 100
      for (i <- 0 until actors) {
        val p = TestProbe()
        p.send(conn, CreateChannel)
        p.expectMsgClass(2.second, classOf[Channel])
      }
      Await.result(gracefulStop(conn, 5 seconds), 6 seconds)
    }
  }
}

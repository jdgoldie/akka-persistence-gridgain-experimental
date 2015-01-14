/*
 *  Copyright 2015 Joshua Goldie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package akka.persistence.gridgain.journal

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.JournalProtocol.{WriteMessageSuccess, WriteMessagesSuccessful, WriteMessages}
import akka.persistence.{PersistentImpl, PersistentRepr, Persistence}
import akka.testkit.{TestProbe, TestKitBase}
import com.typesafe.config.ConfigFactory
import org.gridgain.grid.GridGain
import org.gridgain.grid.cache.GridCache
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll, BeforeAndAfterEach}


class GridGainJournalCacheProjectionSpec extends TestKitBase with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  lazy val config = ConfigFactory.parseString(
    """
      |akka {
      |  persistence {
      |    journal {
      |      plugin = "akka.persistence.gridgain.journal"
      |    }
      |  }
      |  test {
      |    single-expect-default = 20s
      |  }
      |}
    """.stripMargin)

  val grid = TestGrid("src/test/resources/gridgain-config.xml")

  implicit lazy val system = ActorSystem("JournalSpec", config)

  var extension : Persistence = _
  def journal: ActorRef = extension.journalFor(null)

  lazy val cache: GridCache[String, Any] = GridGain.grid().cache[String, Any]("projection-cache")

  //Setup code borrowed from/inspired by krasserm/akka-persistence-testkit
  override protected def beforeAll(): Unit = {

    super.beforeAll()
    extension = Persistence(system)

    //Set up some test data
    val sender = TestProbe()
    val testProbe = TestProbe()

    //Write 5 messages for a single persistenceId
    val msgs = 1 to 5 map { i => PersistentRepr(payload = s"PAYLOAD-VALUE-1-$i", sequenceNr = i, persistenceId = "P-1", sender = sender.ref) }
    journal ! WriteMessages(msgs, testProbe.ref, 1);
    testProbe.expectMsg(WriteMessagesSuccessful)
    1 to 5 foreach { i =>
      testProbe.expectMsgPF() { case WriteMessageSuccess(PersistentImpl(payload, i, "P-1", _, _, _), 1) => payload should equal(s"PAYLOAD-VALUE-1-$i") }
    }

    //Write a single message for a different persistenceId
    val msg = List(PersistentRepr(payload = s"PAYLOAD-VALUE-2-1", sequenceNr = 1, persistenceId = "P-2", sender = sender.ref))
    journal ! WriteMessages(msg, testProbe.ref, 1)
    testProbe.expectMsg(WriteMessagesSuccessful)
    testProbe.expectMsgPF() { case WriteMessageSuccess(PersistentImpl(payload, i, "P-2", _, _, _), 1) => payload should equal(s"PAYLOAD-VALUE-2-1") }

    //Actor->Journal->Cache flow is still async, so timing can be an issue
    Thread.sleep(1000)

  }

  override protected def afterAll(): Unit = shutdown(system)

  "A journal cache projection" must {

    "cache the last message for a persistenceId"  in {
      cache.get("P-1") should equal("PAYLOAD-VALUE-1-5")
    }

    "cache values for different persistenceIds correctly" in {
      cache.get("P-2") should equal("PAYLOAD-VALUE-2-1")
      cache.get("P-1") should equal("PAYLOAD-VALUE-1-5")
    }

  }



}

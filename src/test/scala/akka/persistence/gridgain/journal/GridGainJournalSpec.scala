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

import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory
import org.gridgain.grid.{Grid, GridGain}

/**
 * Basic journal compatability tests
 */
class GridGainJournalSpec extends JournalSpec {

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

  protected override def beforeEach(): Unit = {
    super.beforeEach()
    Thread.sleep(2000) //to help Travis-CI keep up
  }
}
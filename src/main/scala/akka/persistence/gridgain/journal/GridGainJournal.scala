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

import java.util

import akka.actor.ActorLogging
import akka.persistence.journal.{AsyncRecovery, AsyncWriteJournal}
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}
import org.gridgain.grid.lang.GridClosure
import org.gridgain.grid.streamer._
import org.gridgain.grid.streamer.index._
import org.gridgain.grid.{Grid, GridGain}

import scala.collection.JavaConversions
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by jgoldie on 1/6/15.
 */
class GridGainJournal extends AsyncWriteJournal with AsyncRecovery with ActorLogging {

  val grid: Grid = GridGain.start("src/main/resources/gridgain-config.xml")

  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = Future {
    grid.streamer("journal-stream").addEvents(JavaConversions.asJavaCollection(messages.map(p => JournalWrite(p))))
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = Future {
    //Since the stream is append-only, convert the delete to a command that will cause an index update
    if (permanent) {
      grid.streamer("journal-stream").addEvent(JournalHardDelete(persistenceId, toSequenceNr))
    } else {
      grid.streamer("journal-stream").addEvent(JournalSoftDelete(persistenceId, toSequenceNr))
    }
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def asyncWriteConfirmations(confirmations: Seq[PersistentConfirmation]): Future[Unit] = Future { /* not supported */ }

  @deprecated("asyncDeleteMessages will be removed.")
  override def asyncDeleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Future[Unit] = Future { /* not supported */ }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future[Long] {
    Option(indexEntry(persistenceId)) match {
      case Some(x : GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry]) => x.value.highestSeq
      case None => 0L
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = Future {
    val entry : GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry] = indexEntry(persistenceId)
    JavaConversions.asScalaIterator(entry.events.iterator)
      //Filter for JournalWrites where the sequence is in the range given *and( higher than the permDeleteFloor
      .filter({p => p match {
        case JournalWrite(repr) =>
          repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr && repr.sequenceNr > entry.value.permDeleteFloor
        case _ => false
      }})
      //Map the JournalWrite(s) to PersistentRepr instances, updating the deleted flag based on the logicalDeleteFloor
      .map({p => p match {
        case JournalWrite(repr) =>
          PersistentRepr(repr.payload, repr.sequenceNr, repr.persistenceId, (repr.sequenceNr <= entry.value.logicalDeleteFloor), repr.redeliveries,
            repr.confirms, repr.confirmable, repr.confirmMessage, repr.confirmTarget, repr.sender)
      }})
      //Handle the count limit.  Unfortunately, conversion from Long to Int results in loss of precision.  This needs to be fixed, but take was
      //a quick way to get initial implementation done
      .take(if(max > Int.MaxValue.toLong) Int.MaxValue else max.toInt)
      //Ready to do the callback
      .foreach(m => replayCallback(m))
  }

  def indexEntry(key: String) : GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry] = {
    grid.streamer("journal-stream").context.window.index("persistence-id").entry(key)
      .asInstanceOf[GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry]]
  }

}

/**
 * Index based on persistence id that tracks highest sequence number as well as deleted ranges.  The index is also configured to
 * store the events for each persistence id to make replay simpler.
 */
class PersistenceIdIndexUpdater extends GridStreamerIndexUpdater[JournalAction, String, SequenceIndexEntry] {

  override def indexKey(r: JournalAction): String =
    r match {
      case JournalWrite(repr: PersistentRepr) => repr.persistenceId
      case JournalHardDelete(persistenceId: String, _) => persistenceId
      case JournalSoftDelete(persistenceId: String, _) => persistenceId
    }

  //Removing an event should not impact the index.  This might become and issue if the window is reconfigured to expire events
  override def onRemoved(entry: GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry], r: JournalAction): SequenceIndexEntry = entry.value()

  override def onAdded(entry: GridStreamerIndexEntry[JournalAction, String, SequenceIndexEntry], r: JournalAction): SequenceIndexEntry =
    r match {
      case JournalWrite(repr : PersistentRepr) => SequenceIndexEntry(entry.value.permDeleteFloor, entry.value.logicalDeleteFloor, scala.math.max(repr.sequenceNr, entry.value.highestSeq))
      case JournalHardDelete(_, toSequenceNr: Long) => SequenceIndexEntry(toSequenceNr, entry.value.logicalDeleteFloor, entry.value.highestSeq)
      case JournalSoftDelete(_, toSequenceNr: Long) => SequenceIndexEntry(entry.value.permDeleteFloor, toSequenceNr, entry.value.highestSeq)
  }

  override def initialValue(r: JournalAction, key: String): SequenceIndexEntry =
    r match {
      case JournalWrite (repr: PersistentRepr) => SequenceIndexEntry(0L, 0L, repr.sequenceNr)
      case _ => null
    }
}

/**
 * Initial stage for the journal stream.  Adds all events to the window.
 */
class JournalStreamInitialStage extends GridStreamerStage[JournalAction] {

  override def name(): String = "journal-stream-initial-stage"

  override def run(context: GridStreamerContext, rs: util.Collection[JournalAction]): util.Map[String, util.Collection[_]] = {
    val window: GridStreamerWindow[JournalAction] = context.window()
    window.enqueueAll(rs)
    window.clearEvicted
    //null stops flow of events to additional stages.  At some point a projections stage will be added which
    //will get the payload from the write events

    null
  }
}

/** The data the index stores for each persistence id */
case class SequenceIndexEntry(permDeleteFloor: Long, logicalDeleteFloor: Long, highestSeq: Long)

/**
 * Since the streams are append only, the approach is to make the journal stream almost command sourced.
 * These classes are the actions that can be performed on the journal.  The index updater handles
 * processing the action and updating the index correctly.
 */
abstract class JournalAction
case class JournalWrite(repr: PersistentRepr) extends JournalAction
case class JournalHardDelete(persistenceId: String, toSequenceNr: Long) extends JournalAction
case class JournalSoftDelete(persistenceId: String, toSequenceNr: Long) extends JournalAction


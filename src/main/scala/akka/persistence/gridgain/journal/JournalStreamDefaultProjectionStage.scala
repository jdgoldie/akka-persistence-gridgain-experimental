package akka.persistence.gridgain.journal

import java.util

import akka.persistence.{PersistentImpl, PersistentRepr}
import org.gridgain.grid.GridGain
import org.gridgain.grid.cache.GridCache
import org.gridgain.grid.streamer.{GridStreamerContext, GridStreamerStage}

import scala.collection.JavaConversions
import scala.collection.mutable.{MultiMap, Set, HashMap}

/**
 * The base class for the default projection stage.
 */
class JournalStreamDefaultProjectionStage extends GridStreamerStage[PersistentRepr] {

  override def name(): String = "default-projection"

  override def run(ctx: GridStreamerContext, writeEvents: util.Collection[PersistentRepr]): util.Map[String, util.Collection[_]] = {
    val routingMap = new HashMap[String, Set[PersistentRepr]] with MultiMap[String, PersistentRepr]
    JavaConversions.asScalaIterator(writeEvents.iterator).foreach({p =>
      handleEvent(p)
      routeEvent(p) match {
        case Some(stages: List[String]) => stages.foreach({ s =>
          routingMap.addBinding(s, p)
        })
        case _ => None
      }
    })
    val map = new util.HashMap[String, util.Collection[_]]()
    routingMap.keys.foreach({ k => map.put(k, JavaConversions.asJavaCollection(routingMap.get(k).get))})
    map
  }

  /**
   * Implement this method to generate a projection of the events
   * @param evt event to process into the projection
   * @return nothing
   */
  def handleEvent(evt: PersistentRepr) : Unit = ???

  /**
   * Implement this method to route the events to additional stages
   * @param evt event to route
   * @return names of the stages to which the event should be passed
   */
  def routeEvent(evt: PersistentRepr): Option[List[String]] = ???

}

class BasicCacheDefaultProjectionStage extends JournalStreamDefaultProjectionStage {

  lazy val cache: GridCache[String, Any] = GridGain.grid().cache[String, Any]("projection-cache")

  /**
   * An example implementation that simply pushes the payload into the GridGain cache.  A real system
   * will probably need something more complex.
   * @param evt
   * @return
   */
  override def handleEvent(evt: PersistentRepr) : Unit = {
      evt match {
        case PersistentImpl(payload: Any, _, persistenceId: String, _,_,_) => cache.put(persistenceId, payload)
      }
  }


  /**
   * No routing in this example
   * @param evt
   * @return
   */
  override def routeEvent(evt: PersistentRepr): Option[List[String]] = None

}
package mesosphere.marathon
package core.task.tracker.impl

import akka.stream.Materializer
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.storage.repository.InstanceRepository
import mesosphere.marathon.stream.Sink
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
  * Loads all task data into an [[InstanceTracker.InstancesBySpec]] from an [[InstanceRepository]].
  */
private[tracker] class InstancesLoaderImpl(repo: InstanceRepository)(implicit val mat: Materializer)
  extends InstancesLoader {
  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val log = LoggerFactory.getLogger(getClass.getName)

  private val ConcurrentCallLimit = 8

  override def load(): Future[InstanceTracker.InstancesBySpec] = {

    repo.ids()
      .grouped(Int.MaxValue) //TODO: might explode, we need a limit
      .mapConcat { names =>
        log.info(s"About to load ${names.size} tasks")
        names
      }
      .mapAsync(ConcurrentCallLimit)(repo.get)
      .mapConcat(_.toList)
      .runWith(Sink.seq)
      .map { instances =>
        log.info(s"Loaded ${instances.size} tasks")
        InstanceTracker.InstancesBySpec.forInstances(instances)
      }

  }
}

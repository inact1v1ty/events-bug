import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object eventsBug extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = ActorMaterializer()

  val applicationContext = Context[Future, MyState, Any]

  import MyState.globalContext._
  import symbolDsl._

  private val field1 = elementId()
  private val field2 = elementId()

  private val config = KorolevServiceConfig[Future, MyState, Any](
    stateStorage = StateStorage.default(MyState("")),
    router = emptyRouter,
    render = {
      case MyState(value) => 'div(
        'input(
          field1,
          event('input)(fieldChange(field1, field2))
        ),
        'input(
          field2,
          event('input)(fieldChange(field2, field1))
        ),
        'div(
          "Current state:",
          'div(value)
        )
      )
    }
  )

  private def fieldChange(field: ElementId, oppositeField: ElementId): Access => Future[Unit] = {
    access => {
      access.transition(identity).flatMap { _ =>
        access.property(field, 'value).flatMap { value =>
          Future { Thread.sleep(1000) }.flatMap{ _ =>
            access.property(oppositeField).set('value, value).flatMap { _ =>
              access.transition(_ => MyState(value))
            }
          }
        }
      }
    }
  }

  private val route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}

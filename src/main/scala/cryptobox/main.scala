package cryptobox

import cats.effect._
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends IOApp {
  import Service._
  val service = Service[IO](StarBankECDSA.apply, InMemoryStorage.apply)
  val httpApp = HttpApi[IO](service).app

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO](global)
      .bindHttp(8080, "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}

package cryptobox

import cats.effect._
import org.http4s.server.blaze._
import cats.implicits._
import com.starkbank.ellipticcurve.PrivateKey

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends IOApp {

  // TODO configuration file, instead of hardcoding host, port, db location
  val db = "data"
  val host = "localhost"
  val port = 8080

  import Service._

  def run(args: List[String]): IO[ExitCode] = for {
    ecdsa <- StarBankECDSA.of[IO]
    _ = println(s"Reading key database from $db ...")
    storage <- PlainTextFileStorage.of[IO, String, PrivateKey](db)
    service = Service[IO](ecdsa, storage)
    httpApp = HttpApi[IO](service).app
    _ = println(s"Server starting up at $host:$port ...")
    res <- BlazeServerBuilder[IO](global)
      .bindHttp(port, host)
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  } yield res

}
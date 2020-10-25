package cryptobox

import java.nio.file.{Files, Paths}
import java.util.Comparator

import cats.effect._
import com.starkbank.ellipticcurve.PrivateKey
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers._
import org.scalatest.wordspec._

import scala.concurrent.ExecutionContext.Implicits.global

class HttpSpec extends AnyWordSpec with should.Matchers with BeforeAndAfterAll {

  val db = "testdata"

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  import Service._

  val httpApp = {
    val init = for {
      ecdsa <- StarBankECDSA.of[IO]
      storage <- PlainTextFileStorage.of[IO, String, PrivateKey](db)
      service = Service[IO](ecdsa, storage)
      httpApp = HttpApi[IO](service).app
    } yield httpApp
    init.unsafeRunSync()
  }

  override def afterAll(): Unit = {
    Files.list(Paths.get(db)).sorted(Comparator.reverseOrder()).forEach(_.toFile.delete())
    Files.delete(Paths.get(db))
  }

  "Http Api" should {

    "generate key, sign message, and verify signature successfully" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = None).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle"
        )
        resp1 <- httpApp.run(req1)
        handleResp <- resp1.as[CreateResponse]
        req2 <- POST(
          SignRequest(message = "Hello World", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        resp2 <- httpApp.run(req2)
        signResp <- resp2.as[SignResponse]
        req3 <- POST(
          VerifyRequest(message = "Hello World", signature = signResp.signature, handle = handleResp.handle).asJson,
          //uri"/rpc/verify"
          uri"" / "rest" / "handle" / handleResp.handle / "signature" / "verification"
        )
        resp3 <- httpApp.run(req3)
      } yield resp3

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Ok
      response.as[VerifyResponse].unsafeRunSync() shouldBe VerifyResponse(verified = true)
    }

    "fail if handles mixed-up" in {
      val program = for {
        dummyReq <- PUT( //POST(
          CreateRequest(handle = Some("bla")).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle" / "bla"
        )
        _ <- httpApp.run(dummyReq)
        req1 <- PUT( //POST(
          CreateRequest(handle = None).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle"
        )
        resp1 <- httpApp.run(req1)
        handleResp <- resp1.as[CreateResponse]
        req2 <- POST(
          SignRequest(message = "Hello World", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        resp2 <- httpApp.run(req2)
        signResp <- resp2.as[SignResponse]
        req3 <- POST(
          VerifyRequest(message = "Hello World", signature = signResp.signature, handle = "bla").asJson,
          //uri"/rpc/verify"
          uri"" / "rest" / "handle" / "bla" / "signature" / "verification"
        )
        resp3 <- httpApp.run(req3)
      } yield resp3

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Ok
      response.as[VerifyResponse].unsafeRunSync() shouldBe VerifyResponse(verified = false)
    }

    "fail if signatures mixed-up" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = None).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle"
        )
        resp1 <- httpApp.run(req1)
        handleResp <- resp1.as[CreateResponse]
        dummyReq <- POST(
          SignRequest(message = "Lorem Ipsum", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        dummyResp <- httpApp.run(dummyReq)
        dummySig <- dummyResp.as[SignResponse]
        req2 <- POST(
          SignRequest(message = "Hello World", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        resp2 <- httpApp.run(req2)
        signResp <- resp2.as[SignResponse]
        req3 <- POST(
          VerifyRequest(message = "Hello World", signature = dummySig.signature, handle = handleResp.handle).asJson,
          //uri"/rpc/verify"
          uri"" / "rest" / "handle" / handleResp.handle / "signature" / "verification"
        )
        resp3 <- httpApp.run(req3)
      } yield resp3

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Ok
      response.as[VerifyResponse].unsafeRunSync() shouldBe VerifyResponse(verified = false)
    }

    "fail if messages mixed-up" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = None).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle"
        )
        resp1 <- httpApp.run(req1)
        handleResp <- resp1.as[CreateResponse]
        req2 <- POST(
          SignRequest(message = "Hello World", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        resp2 <- httpApp.run(req2)
        signResp <- resp2.as[SignResponse]
        req3 <- POST(
          VerifyRequest(message = "Lorem Ipsum", signature = signResp.signature, handle = handleResp.handle).asJson,
          //uri"/rpc/verify"
          uri"" / "rest" / "handle" / handleResp.handle / "signature" / "verification"
        )
        resp3 <- httpApp.run(req3)
      } yield resp3

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Ok
      response.as[VerifyResponse].unsafeRunSync() shouldBe VerifyResponse(verified = false)
    }

    "fail to sign with non-existent handle" in {
      val program = for {
        req <- POST(
          SignRequest(message = "Hello World", handle = "foo").asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / "foo" / "signature"
        )
        resp <- httpApp.run(req)
      } yield resp

      val response = program.unsafeRunSync()

      response.status shouldBe Status.NotFound
    }

    "fail to verify with non-existent handle" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = None).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle"
        )
        resp1 <- httpApp.run(req1)
        handleResp <- resp1.as[CreateResponse]
        req2 <- POST(
          SignRequest(message = "Hello World", handle = handleResp.handle).asJson,
          //uri"/rpc/sign"
          uri"" / "rest" / "handle" / handleResp.handle / "signature"
        )
        resp2 <- httpApp.run(req2)
        signResp <- resp2.as[SignResponse]
        req3 <- POST(
          VerifyRequest(message = "Hello World", signature = signResp.signature, handle = "bar").asJson,
          //uri"/rpc/verify"
          uri"" / "rest" / "handle" / "bar" / "signature" / "verification"
        )
        resp3 <- httpApp.run(req3)
      } yield resp3

      val response = program.unsafeRunSync()

      response.status shouldBe Status.NotFound
    }

    "create requested handle if available" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = Some("baz")).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle" / "baz"
        )
        resp1 <- httpApp.run(req1)
      } yield resp1

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Ok
      response.as[CreateResponse].unsafeRunSync() shouldBe CreateResponse(handle = "baz")
    }

    "fail to generate keys for a handle already in use" in {
      val program = for {
        req1 <- PUT( //POST(
          CreateRequest(handle = Some("hello")).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle" / "hello"
        )
        resp1 <- httpApp.run(req1)
        req2 <- PUT( //POST(
          CreateRequest(handle = Some("hello")).asJson,
          //uri"/rpc/create"
          uri"" / "rest" / "handle" / "hello"
        )
        resp2 <- httpApp.run(req2)
      } yield resp2

      val response = program.unsafeRunSync()

      response.status shouldBe Status.Conflict
    }

  }

}

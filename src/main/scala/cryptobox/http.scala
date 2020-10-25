package cryptobox

import cats.implicits._
import cats.effect._
import io.circe.{Encoder, Json}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router

case class CreateRequest(
    handle: Option[String]
)

object CreateRequest {
  implicit def decoder[F[_]: Async] = jsonOf[F, CreateRequest]
}

case class CreateResponse(
    handle: String
)

object CreateResponse {
  implicit def decoder[F[_]: Async] = jsonOf[F, CreateResponse]
}

case class SignRequest(
    message: String,
    handle: String
)

object SignRequest {
  implicit def decoder[F[_]: Async] = jsonOf[F, SignRequest]
}

case class SignResponse(
    signature: String
)

object SignResponse {
  implicit def decoder[F[_]: Async] = jsonOf[F, SignResponse]
}

case class VerifyRequest(
    message: String,
    signature: String,
    handle: String
)

object VerifyRequest {
  implicit def decoder[F[_]: Async] = jsonOf[F, VerifyRequest]
}

case class VerifyResponse(
    verified: Boolean
)

object VerifyResponse {
  implicit def decoder[F[_]: Async] = jsonOf[F, VerifyResponse]
}

case class HttpApi[F[_]: Async](service: Service[F]) {
  val rpcApi = HttpRoutes.of[F] {

    // An RPC-like api
    case req @ POST -> Root / "create" =>
      val result = for {
        createReq <- req.as[CreateRequest]
        // TODO request validation
        handle <- createReq.handle match {
          case Some(handle) => service.create(handle)
          case None => service.create
        }
      } yield CreateResponse(handle)
      result.map(responseOk(_)).recover(handler)

    case req @ POST -> Root / "sign"   =>
      val result = for {
        signReq <- req.as[SignRequest]
        // TODO request validation
        signature <- service.sign(signReq.message, signReq.handle)
      } yield SignResponse(signature)
      result.map(responseOk(_)).recover(handler)

    case req @ POST -> Root / "verify" =>
      val result = for {
        verifyReq <- req.as[VerifyRequest]
        // TODO request validation
        verified <- service.verify(verifyReq.message, verifyReq.signature, verifyReq.handle)
      } yield VerifyResponse(verified)
      result.map(responseOk(_)).recover(handler)

  }

  // A REST-like api
  val restApi = HttpRoutes.of[F] {
    case req @ PUT -> Root / "handle" =>
      val result = for {
        createReq <- req.as[CreateRequest]
        // TODO request validation
        handle <- service.create
      } yield CreateResponse(handle)
      result.map(responseOk(_)).recover(handler)

    case req @ PUT -> Root / "handle" / handle =>
      val result = for {
        createReq <- req.as[CreateRequest]
        // TODO request validation
        handle <- service.create(handle)
      } yield CreateResponse(handle)
      result.map(responseOk(_)).recover(handler)

    case req @ POST -> Root / "handle" / handle / "signature" =>
      val result = for {
        signReq <- req.as[SignRequest]
        // TODO request validation
        signature <- service.sign(signReq.message, handle)
      } yield SignResponse(signature)
      result.map(responseOk(_)).recover(handler)


    case req @ POST -> Root / "handle" / handle / "signature" / "verification" =>
      val result = for {
        verifyReq <- req.as[VerifyRequest]
        // TODO request validation
        verified <- service.verify(verifyReq.message, verifyReq.signature, handle)
      } yield VerifyResponse(verified)
      result.map(responseOk(_)).recover(handler)
  }

  val app = Router("/rpc" -> rpcApi, "/rest" -> restApi).orNotFound

  def responseOk[T: Encoder](value: T): Response[F] =
    response(Status.Ok, Some(value))

  def handler: PartialFunction[Throwable, Response[F]] = {
    // TODO logging
    case NotFoundException(msg) => response(Status.NotFound, None)
    case AlreadyExistsException(msg) => response(Status.Conflict, None)
    case e => response(Status.InternalServerError, None)
  }

  def response[T: Encoder](status: Status, value: Option[T]): Response[F] =
    value match {
      case Some(v) =>
        Response(
          status = status,
          body = implicitly[EntityEncoder[F, Json]].toEntity(v.asJson).body
        )
      case None =>
        Response(status = status)
    }
}

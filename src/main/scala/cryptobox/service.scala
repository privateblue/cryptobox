package cryptobox

import java.util.UUID

import cats.effect.Sync
import cats.implicits._
import com.starkbank.ellipticcurve.utils.ByteString
import com.starkbank.ellipticcurve.{PrivateKey, Signature}

case class Service[F[_]](
    dsa: DSA[F],
    storage: Storage[F, String, String, PrivateKey, String]
)(implicit F: Sync[F]) {
  def create: F[String] = {
    val handle = UUID.randomUUID().toString
    create(handle)
  }

  def create(handle: String): F[String] =
    for {
      privateKey <- dsa.generate
      _ <- storage.set(handle, privateKey)
    } yield handle

  def sign(msg: String, handle: String): F[String] =
    for {
      privateKey <- storage.get(handle)
      signature <- dsa.sign(msg, privateKey)
      base64 <- F.delay(signature.toBase64)
    } yield base64

  def verify(msg: String, signatureString: String, handle: String): F[Boolean] =
    for {
      privateKey <- storage.get(handle)
      signature <- F.delay(Signature.fromBase64(new ByteString(signatureString.getBytes)))
      verified <- dsa.verify(msg, signature, privateKey.publicKey())
    } yield verified
}

object Service {
  implicit def stringNoopMarshaller[F[_]: Sync] =
    Marshalling[F, String, String](identity)(identity)
  implicit def privateKeyStringMarshalling[F[_]: Sync] =
    Marshalling[F, PrivateKey, String](_.toPem)(PrivateKey.fromPem)
}

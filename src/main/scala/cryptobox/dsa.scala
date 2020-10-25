package cryptobox

import cats.effect.Sync
import com.starkbank.ellipticcurve.{Ecdsa, PrivateKey, PublicKey, Signature}

trait DSA[F[_]] {
  def generate: F[PrivateKey]
  def sign(msg: String, privateKey: PrivateKey): F[Signature]
  def verify(
      msg: String,
      signature: Signature,
      publicKey: PublicKey
  ): F[Boolean]
}

object StarBankECDSA {
  def apply[F[_]](implicit F: Sync[F]): DSA[F] =
    new DSA[F] {
      override def generate: F[PrivateKey] = F.delay {
        new PrivateKey()
      }

      override def sign(msg: String, privateKey: PrivateKey): F[Signature] =
        F.delay {
          Ecdsa.sign(msg, privateKey)
        }

      override def verify(
          msg: String,
          signature: Signature,
          publicKey: PublicKey
      ): F[Boolean] = F.delay {
        Ecdsa.verify(msg, signature, publicKey)
      }
    }

  def of[F[_]](implicit F: Sync[F]): F[DSA[F]] = F.pure(apply)
}

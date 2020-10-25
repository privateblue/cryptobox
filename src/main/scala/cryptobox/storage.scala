package cryptobox

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._

trait Storage[F[_], K, KM, V, VM] {
  def get(key: K): F[V]
  def set(key: K, value: V): F[Unit]
}

case class NotFoundException(msg: String) extends Exception(msg)
case class AlreadyExistsException(msg: String) extends Exception(msg)

trait Marshaller[F[_], T, M] {
  def marshal(value: T): F[M]
}

trait Unmarshaller[F[_], T, M] {
  def unmarshal(repr: M): F[T]
}

object Marshalling {
  def apply[F[_],T, M](
      to: T => M
  )(from: M => T)(implicit F: Sync[F]): Marshaller[F, T, M] with Unmarshaller[F, T, M] =
    new Marshaller[F, T, M] with Unmarshaller[F, T, M] {
      def marshal(value: T): F[M] = F.delay(to(value))
      def unmarshal(repr: M): F[T] = F.delay(from(repr))
    }
}

object InMemoryStorage {
  def apply[F[_], K, V](implicit
      F: Sync[F],
      kmarshaller: Marshaller[F, K, String],
      vmarshaller: Marshaller[F, V, String],
      vunmarshaller: Unmarshaller[F, V, String]
  ): Storage[F, K, String, V, String] = {
    val entries = Ref.unsafe[F, Map[String, String]](Map.empty)
    build(entries)
  }

  def of[F[_], K, V](implicit
      F: Sync[F],
      kmarshaller: Marshaller[F, K, String],
      vmarshaller: Marshaller[F, V, String],
      vunmarshaller: Unmarshaller[F, V, String]
  ): F[Storage[F, K, String, V, String]] =
    for {
      entries <- Ref[F].of(Map.empty[String, String])
    } yield build(entries)

  def build[F[_], K, V](entries: Ref[F, Map[String, String]])(implicit
      F: Sync[F],
      kmarshaller: Marshaller[F, K, String],
      vmarshaller: Marshaller[F, V, String],
      vunmarshaller: Unmarshaller[F, V, String]
  ): Storage[F, K, String, V, String] =
    new Storage[F, K, String, V, String] {
      def get(key: K): F[V] = for {
        map <- entries.get
        keyS <- kmarshaller.marshal(key)
        maybeValueS = map.get(keyS)
        value <- maybeValueS match {
          case Some(v) => vunmarshaller.unmarshal(v)
          case None => F.raiseError(NotFoundException(s"Key $keyS not found"))
        }
      } yield value

      def set(key: K, value: V): F[Unit] = {
        for {
          keyS <- kmarshaller.marshal(key)
          valueS <- vmarshaller.marshal(value)
          success <- entries.modify(m =>
            if (m.contains(keyS)) (m, false) else (m + (keyS -> valueS), true)
          )
          _ <-
            if (success) F.pure(())
            else F.raiseError(AlreadyExistsException(s"Key $keyS already exists"))
        } yield ()
      }
    }
}

package cryptobox

import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import cats.syntax.all._

import scala.io.Source

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
  def apply[F[_], T, M](
      to: T => M
  )(
      from: M => T
  )(implicit
      F: Sync[F]
  ): Marshaller[F, T, M] with Unmarshaller[F, T, M] =
    new Marshaller[F, T, M] with Unmarshaller[F, T, M] {
      def marshal(value: T): F[M] = F.delay(to(value))
      def unmarshal(repr: M): F[T] = F.delay(from(repr))
    }
}

class InMemoryStorage[F[_], K, V](
    entries: Ref[F, Map[String, String]]
)(implicit
    F: Sync[F],
    kmarshaller: Marshaller[F, K, String],
    vmarshaller: Marshaller[F, V, String],
    vunmarshaller: Unmarshaller[F, V, String]
) extends Storage[F, K, String, V, String] {
  def get(key: K): F[V] =
    for {
      map <- entries.get
      keyS <- kmarshaller.marshal(key)
      maybeValueS = map.get(keyS)
      value <- maybeValueS match {
        case Some(v) => vunmarshaller.unmarshal(v)
        case None    => F.raiseError(NotFoundException(s"Key $keyS not found"))
      }
    } yield value

  def set(key: K, value: V): F[Unit] =
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
    new InMemoryStorage(entries)
}

abstract class PlainTextFileStorage[F[_], K, V](
    entries: Ref[F, Map[String, String]]
)(implicit
    F: Sync[F],
    kmarshaller: Marshaller[F, K, String],
    vmarshaller: Marshaller[F, V, String],
    vunmarshaller: Unmarshaller[F, V, String]
) extends InMemoryStorage[F, K, V](entries) {
  protected def append(key: String, value: String): F[Unit]

  override def set(key: K, value: V): F[Unit] =
    for {
      keyS <- kmarshaller.marshal(key)
      valueS <- vmarshaller.marshal(value)
      _ <- super.set(key, value)
      _ <- append(keyS, valueS)
    } yield ()
}

object PlainTextFileStorage {
  def of[F[_], K, V](path: String)(implicit
      F: Concurrent[F],
      kmarshaller: Marshaller[F, K, String],
      vmarshaller: Marshaller[F, V, String],
      vunmarshaller: Unmarshaller[F, V, String]
  ): F[PlainTextFileStorage[F, K, V]] = {
    val source = Resource.make { F.delay(Source.fromFile(path)) } { src =>
      F.delay(src.close).handleErrorWith(_ => F.pure(()))
    }
    for {
      _ <- F.delay {
        if (!Files.exists(Paths.get(path))) Files.createFile(Paths.get(path))
      }
      map <- source.use[F, Map[String, String]](src => readDb(src))
      entries <- Ref[F].of(map)
      dbWrite <- Semaphore[F](1)
    } yield new PlainTextFileStorage[F, K, V](entries) {
      protected def append(key: String, value: String): F[Unit] =
        for {
          _ <- dbWrite.acquire
          record = s"$key\n$value"
          _ <- F.delay(Files.write(Paths.get(path), record.getBytes, StandardOpenOption.APPEND))
          _ <- dbWrite.release
        } yield ()
    }
  }

  def readDb[F[_]](src: Source)(implicit F: Concurrent[F]): F[Map[String, String]] =
    F.delay {
      val acc = (Option.empty[String], Option.empty[String], Map.empty[String, String])
      val (_, _, map) = src.getLines().foldLeft(acc) {
        case ((None, None, map), l) => (Some(l), None, map)
        case ((Some(k), None, map), l) if l.startsWith("-----") => (Some(k), Some(l), map)
        case ((Some(k), Some(v), map), l) if l.startsWith("-----") => (None, None, map + (k -> v.concat("\n").concat(l)))
        case ((Some(k), Some(v), map), l) => (Some(k), Some(v.concat("\n").concat(l)), map)
        case ((k, v, map), l) => (k, v, map)
      }
      map
    }
}

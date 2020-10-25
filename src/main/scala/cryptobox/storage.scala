package cryptobox

import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._

import scala.io.Source
import scala.jdk.CollectionConverters._

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

class PlainTextFileStorage[F[_], K, V](
    path: String,
    entries: Ref[F, Map[String, String]]
)(implicit
    F: Sync[F],
    kmarshaller: Marshaller[F, K, String],
    vmarshaller: Marshaller[F, V, String],
    vunmarshaller: Unmarshaller[F, V, String]
) extends InMemoryStorage[F, K, V](entries) {
  protected def write(key: String, value: String): F[Unit] =
    F.delay{
      Files.write(Paths.get(s"$path/$key"), value.getBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

  override def set(key: K, value: V): F[Unit] =
    for {
      keyS <- kmarshaller.marshal(key)
      valueS <- vmarshaller.marshal(value)
      _ <- super.set(key, value)
      _ <- write(keyS, valueS)
    } yield ()
}

object PlainTextFileStorage {
  def of[F[_], K, V](path: String)(implicit
      F: Sync[F],
      kmarshaller: Marshaller[F, K, String],
      vmarshaller: Marshaller[F, V, String],
      vunmarshaller: Unmarshaller[F, V, String]
  ): F[PlainTextFileStorage[F, K, V]] =
    for {
      _ <- F.delay { if (!Files.isDirectory(Paths.get(path))) Files.createDirectory(Paths.get(path)) }
      map <- read(path)
      entries <- Ref[F].of(map)
    } yield new PlainTextFileStorage[F, K, V](path, entries)

  private def read[F[_]](path: String)(implicit F: Sync[F]): F[Map[String, String]] =
    Files.list(Paths.get(path)).iterator().asScala.toList
      .map(p => p.getFileName.toString -> mkResource(p))
      .map(e => readFile(e._1, e._2))
      .sequence
      .map(_.toMap)

  private def mkResource[F[_]](p: Path)(implicit F: Sync[F]): Resource[F, Source] =
    Resource.make { F.delay(Source.fromFile(p.toFile)) } { src =>
      F.delay(src.close).handleErrorWith(_ => F.pure(()))
    }

  private def readFile[F[_]](name: String, r: Resource[F, Source])(implicit F: Sync[F]): F[(String, String)] =
    r.use[F, (String, String)] { src => F.delay(name -> src.getLines().mkString("\n")) }
}

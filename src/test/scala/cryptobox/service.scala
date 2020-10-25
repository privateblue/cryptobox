package cryptobox

import cats.effect.IO
import com.starkbank.ellipticcurve.PrivateKey
import org.scalatest.matchers._
import org.scalatest.wordspec._

class ServiceSpec extends AnyWordSpec with should.Matchers {

  import Service._
  val init = for {
    dsa <- StarBankECDSA.of[IO]
    storage <- InMemoryStorage.of[IO, String, PrivateKey]
  } yield Service[IO](dsa, storage)

  "Service" should {

    "generate keys, sign messages, and verify signatures successfully" in {
      val program = for {
        service <- init
        handle1 <- service.create
        handle2 <- service.create
        msg1 = "Hello world"
        msg2 = "Lorem ipsum"
        signature1 <- service.sign(msg1, handle1)
        signature2 <- service.sign(msg2, handle2)
        verified1 <- service.verify(msg1, signature1, handle1)
        verified2 <- service.verify(msg2, signature2, handle2)
      } yield (verified1, verified2)

      program.unsafeRunSync() shouldBe (true, true)
    }

    "fail if handles mixed-up" in {
      val program = for {
        service <- init
        handle1 <- service.create
        handle2 <- service.create
        msg1 = "Hello world"
        msg2 = "Lorem ipsum"
        signature1 <- service.sign(msg1, handle1)
        signature2 <- service.sign(msg2, handle2)
        verified1 <- service.verify(msg1, signature1, handle2)
        verified2 <- service.verify(msg2, signature2, handle2)
      } yield (verified1, verified2)

      program.unsafeRunSync() shouldBe (false, true)
    }

    "fail if signatures mixed-up" in {
      val program = for {
        service <- init
        handle1 <- service.create
        handle2 <- service.create
        msg1 = "Hello world"
        msg2 = "Lorem ipsum"
        signature1 <- service.sign(msg1, handle1)
        signature2 <- service.sign(msg2, handle2)
        verified1 <- service.verify(msg1, signature1, handle1)
        verified2 <- service.verify(msg2, signature1, handle2)
      } yield (verified1, verified2)

      program.unsafeRunSync() shouldBe (true, false)
    }

    "fail if messages mixed up" in {
      val program = for {
        service <- init
        handle1 <- service.create
        handle2 <- service.create
        msg1 = "Hello world"
        msg2 = "Lorem ipsum"
        signature1 <- service.sign(msg1, handle1)
        signature2 <- service.sign(msg2, handle2)
        verified1 <- service.verify(msg1, signature1, handle1)
        verified2 <- service.verify(msg1, signature2, handle2)
      } yield (verified1, verified2)

      program.unsafeRunSync() shouldBe (true, false)
    }

    "fail to sign with non-existent handle" in {
      val program = for {
        service <- init
        msg = "Hello world"
        handle = "bla"
        signature <- service.sign(msg, handle)
      } yield signature

      an[NotFoundException] should be thrownBy program.unsafeRunSync()
    }

    "fail to verify with non-existent handle" in {
      val program = for {
        service <- init
        handle1 <- service.create
        msg = "Hello world"
        signature <- service.sign(msg, handle1)
        handle2 = "bla"
        signature <- service.verify(msg, signature, handle2)
      } yield signature

      an[NotFoundException] should be thrownBy program.unsafeRunSync()
    }

    "fail to generate keys for a handle already in use" in {
      val program = for {
        service <- init
        handle1 <- service.create("bla")
        handle2 <- service.create("bla")
      } yield handle2

      an[AlreadyExistsException] should be thrownBy program.unsafeRunSync()
    }

  }

}

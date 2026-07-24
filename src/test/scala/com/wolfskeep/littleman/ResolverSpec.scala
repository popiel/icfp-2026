package com.wolfskeep.littleman

import com.wolfskeep.littleman.model._
import com.wolfskeep.littleman.parse.Loader
import com.wolfskeep.littleman.resolve.Resolver
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ResolverSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  private def resolve(s: String): ResolvedProgram =
    Loader.load(grid(s)).map(Resolver.resolve).toOption.get

  private def targetAt(rp: ResolvedProgram, x: Int, y: Int): ResolvedTargets =
    rp.targets.getOrElse(Point(x, y), ResolvedTargets.NotPipe)

  "Resolver" should {
    "resolve an 's' instruction to its nearest outgoing pipe" in {
      val rp = resolve(
        """
          #+--+>>+-+
          #|@s|  |H|
          #+--+  +-+""")
      targetAt(rp, 2, 1) shouldBe ResolvedTargets.Nearest(
        rp.program.pipes.pipes.headOption)
    }

    "resolve an 'r' instruction to its nearest incoming pipe" in {
      val rp = resolve(
        """
          #+--+>>+--+
          #|@s|  |rH|
          #+--+  +--+""")
      val destRoom = rp.program.rooms.rooms.find(_.topLeft == Point(6, 0)).get
      val incoming = rp.program.pipes.pipes.filter(_.dest == PipeDest.Room(destRoom.id))
      targetAt(rp, 7, 1) shouldBe ResolvedTargets.Nearest(incoming.headOption)
    }

    "resolve an 'S' to all outgoing pipes in reading order" in {
      // room1 has two outgoing pipes: one east, one south
      val rp = resolve(
        """
          #+--+>>+--+
          #|@S|  |H |
          #+--+  +--+
          # v
          # |
          # v
          # +-+
          # |H|
          # +-+""")
      val room1 = rp.program.rooms.rooms.head
      val outs = rp.program.pipes.pipes.filter(_.sourceRoomId == room1.id)
      outs should have size 2
      targetAt(rp, 2, 1) shouldBe ResolvedTargets.All(outs)
    }

    "resolve an 'R' to all incoming pipes in reading order (not by proximity)" in {
      val rp = resolve(
        """
          #+--+>>+--+>>+--+
          #|@s|  |R |  |H |
          #+--+  +--+  +--+""")
      val destRoom = rp.program.rooms.rooms.find(_.topLeft == Point(6, 0)).get
      val dests = rp.program.pipes.pipes.filter(_.dest == PipeDest.Room(destRoom.id))
      targetAt(rp, 7, 1) shouldBe ResolvedTargets.All(dests)
    }

    "return NotPipe for a non-pipe instruction" in {
      val rp = resolve(
        """
          #+--+
          #|@H|
          #+--+""")
      targetAt(rp, 2, 1) shouldBe ResolvedTargets.NotPipe
    }

    "return Nearest(None) for an 's' in a room with no outgoing pipe (no-pipe)" in {
      val rp = resolve(
        """
          #+---+
          #|@s |
          #+---+""")
      targetAt(rp, 2, 1) shouldBe ResolvedTargets.Nearest(None)
    }

    "break ties by reading order (top-to-bottom, left-to-right)" in {
      // `s` sees two outgoing pipes from its room; the nearer (by Manhattan)
      // distance wins, ties by reading order. Here the east pipe is nearer.
      val rp = resolve(
        """
          #+--+>>+--+
          #|@s|  |H |
          #+--+  +--+
          # v
          # |
          # v
          # +-+
          # |H|
          # +-+""")
      val nearest = targetAt(rp, 2, 1).asInstanceOf[ResolvedTargets.Nearest].pipe
      nearest shouldBe defined
      nearest.get.sourceRoomId shouldBe 0
    }

    "resolve 'q' to the nearest incoming pipe (for counting values)" in {
      val rp = resolve(
        """
          #+--+>>+--+
          #|@s|  |qH|
          #+--+  +--+""")
      targetAt(rp, 7, 1).asInstanceOf[ResolvedTargets.Nearest].pipe shouldBe defined
    }

    "resolve 'U' to all incoming pipes (like R, but with turn-away)" in {
      val rp = resolve(
        """
          #+--+>>+--+
          #|@s|  |UH|
          #+--+  +--+""")
      targetAt(rp, 7, 1) shouldBe a [ResolvedTargets.All]
    }
  }
}
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

    "resolve an 'R' to all incoming pipes ordered by proximity (distance, then reading order)" in {
      val rp = resolve(
        """
          #+--+>>+--+>>+--+
          #|@s|  |R |  |H |
          #+--+  +--+  +--+""")
      val destRoom = rp.program.rooms.rooms.find(_.topLeft == Point(6, 0)).get
      val dests = rp.program.pipes.pipes.filter(_.dest == PipeDest.Room(destRoom.id))
      targetAt(rp, 7, 1) shouldBe ResolvedTargets.All(dests)
    }

    "order R's incoming pipes by Manhattan distance with reading-order tiebreak" in {
      // Two incoming pipes into the right room R:
      //   (a) west '>>' from the left room into R's LEFT wall (destCell (6,1))
      //   (b) south '^'  from H's top wall, flowing up into R's BOTTOM wall
      // R occupies rows 0..2, cols 6..13. H occupies rows 4..6, cols 11..14.
      // The vertical pipe: source '^' at (12,3) with backward cell (12,4)=H
      // top wall; terminal '^' at... actually the source 'is' the start. We
      // need two cells: a start '^' (attached to H's top) and a terminal '^'
      // whose forward cell (one above) is R's bottom wall at (12,2).
      // So: start '^' at (12,4)? No — (12,4) is H's top wall (owned).
      // Correct: start '^' at (12,3) backward (12,4)=H top; flows up; the
      // next free cell above is (12,2)? No, (12,2)=R bottom wall (owned).
      // So we need a 2-row gap; put H at rows 5..7 so free rows 3,4.
      // start '^' at (12,4) backward (12,5)=H top; flows up; cell (12,3) free.
      // Make (12,3) a terminal '^': forward (12,2)=R bottom wall -> terminal.
      // Cells: (12,4),(12,3). Length 2.
      val rp = resolve(
        """
          #+--+>>+------+
          #|@s|  |R  H  |
          #+--+  +------+
          #             ^
          #             ^
          #         +---+
          #         |H  |
          #         +---+""")
      val rRoom = rp.program.rooms.rooms.find(_.topLeft == Point(6, 0)).get
      val all = rp.program.pipes.pipes.filter(_.dest == PipeDest.Room(rRoom.id))
      all should have size 2
      val rPos = rRoom.interiorOrigin
      val t = targetAt(rp, rPos.x, rPos.y).asInstanceOf[ResolvedTargets.All]
      t.pipes should have size 2
      val d1 = rPos.manhattan(t.pipes.head.destCell)
      val d2 = rPos.manhattan(t.pipes.last.destCell)
      d1 should be <= d2
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
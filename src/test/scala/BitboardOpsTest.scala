package test

import munit.FunSuite
import domain.*
import logic.BitboardOps

class BitboardOpsTest extends FunSuite:

  test("Square.fromIndex should convert valid indices to squares") {
    val square = Square.fromIndex(0)
    assert(square.isDefined, "Square 0 should be valid")
    
    val square63 = Square.fromIndex(63)
    assert(square63.isDefined, "Square 63 should be valid")
  }

  test("Square.fromIndex should reject invalid indices") {
    assert(Square.fromIndex(-1).isEmpty, "Negative index should be invalid")
    assert(Square.fromIndex(64).isEmpty, "Index 64 should be invalid")
    assert(Square.fromIndex(100).isEmpty, "Large index should be invalid")
  }

  test("Square arithmetic should work correctly") {
    val sq1 = Square.fromIndex(0).get
    val sq2 = Square.fromIndex(1).get
    // Squares should be ordered
    assert(sq1.index < sq2.index)
  }

  test("Bitboard.contains should detect pieces correctly") {
    val bb = Bitboard.fromSquares(Square.fromIndex(0).get)
    assert(bb.contains(Square.fromIndex(0).get), "Should contain square 0")
    assert(!bb.contains(Square.fromIndex(1).get), "Should not contain square 1")
  }

  test("Bitboard.union should combine bitboards") {
    val bb1 = Bitboard.fromSquares(Square.fromIndex(0).get)
    val bb2 = Bitboard.fromSquares(Square.fromIndex(1).get)
    val union = bb1.union(bb2)
    
    assert(union.contains(Square.fromIndex(0).get), "Union should contain square 0")
    assert(union.contains(Square.fromIndex(1).get), "Union should contain square 1")
    assert(!union.contains(Square.fromIndex(2).get), "Union should not contain square 2")
  }

  test("Bitboard.intersection should find common squares") {
    val bb1 = Bitboard.fromSquares(Square.fromIndex(0).get, Square.fromIndex(1).get)
    val bb2 = Bitboard.fromSquares(Square.fromIndex(1).get, Square.fromIndex(2).get)
    val intersection = bb1.intersection(bb2)
    
    assert(intersection.contains(Square.fromIndex(1).get), "Intersection should contain square 1")
    assert(!intersection.contains(Square.fromIndex(0).get), "Intersection should not contain square 0")
    assert(!intersection.contains(Square.fromIndex(2).get), "Intersection should not contain square 2")
  }

  test("Bitboard.complement should invert all bits") {
    val bb = Bitboard.fromSquares(Square.fromIndex(0).get)
    val comp = bb.complement
    
    assert(!comp.contains(Square.fromIndex(0).get), "Complement should not contain square 0")
    assert(comp.contains(Square.fromIndex(1).get), "Complement should contain square 1")
  }

  test("getAllOccupied should return all occupied squares") {
    val world = World.initial
    val occupied = BitboardOps.getAllOccupied(world)
    
    // Initial position has 32 pieces (16 white + 16 black)
    // Check that some squares are occupied
    assert(occupied.contains(Square.fromIndex(0).get), "Square 0 (a1) should be occupied in initial position")
    assert(occupied.contains(Square.fromIndex(56).get), "Square 56 (a8) should be occupied in initial position")
  }

  test("getOccupiedByColor should distinguish white and black pieces") {
    val world = World.initial
    val whiteOccupied = BitboardOps.getOccupiedByColor(world, Color.White)
    val blackOccupied = BitboardOps.getOccupiedByColor(world, Color.Black)
    
    // White pieces are at indices 0-15
    assert(whiteOccupied.contains(Square.fromIndex(0).get), "White should have piece at a1")
    assert(!whiteOccupied.contains(Square.fromIndex(56).get), "White should not have piece at a8")
    
    // Black pieces are at indices 48-63
    assert(blackOccupied.contains(Square.fromIndex(56).get), "Black should have piece at a8")
    assert(!blackOccupied.contains(Square.fromIndex(0).get), "Black should not have piece at a1")
  }

  test("pawnAttackSquares should generate correct attack pattern") {
    val sq = Square.fromIndex(0).get  // a1
    val attacks = BitboardOps.getPawnAttacks(sq, Color.White)
    
    // White pawn at a1 attacks b2 (index 9)
    assert(attacks.contains(Square.fromIndex(9).get), "White pawn at a1 should attack b2")
  }

  test("knightAttackSquares should generate correct knight moves") {
    val sq = Square.fromIndex(28).get  // e4 (center square)
    val attacks = BitboardOps.getKnightAttacks(sq)
    
    // Knight at e4 should have 8 possible moves
    var count = 0
    for i <- 0 to 63 do
      if attacks.contains(Square.fromIndex(i).get) then count += 1
    assert(count == 8, s"Knight at center should have 8 moves, got $count")
  }

  test("kingAttackSquares should generate correct king moves") {
    val sq = Square.fromIndex(28).get  // e4
    val attacks = BitboardOps.getKingAttacks(sq)
    
    // King at e4 should have 8 adjacent squares
    var count = 0
    for i <- 0 to 63 do
      if attacks.contains(Square.fromIndex(i).get) then count += 1
    assert(count == 8, s"King at center should have 8 moves, got $count")
  }

  test("Bitboard operations should be composable") {
    val sq1 = Square.fromIndex(0).get
    val sq2 = Square.fromIndex(1).get
    val sq3 = Square.fromIndex(2).get
    
    val bb = Bitboard.fromSquares(sq1, sq2)
      .union(Bitboard.fromSquares(sq3))
    
    assert(bb.contains(sq1) && bb.contains(sq2) && bb.contains(sq3))
  }

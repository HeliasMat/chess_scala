package logic

import domain._

// Bitboard operations for piece movement calculations
object BitboardOps:

  // Precomputed attack tables for non-sliding pieces
  private val pawnAttacks: Map[Color, Array[Bitboard]] = Map(
    Color.White -> Array.fill(64)(Bitboard.empty),
    Color.Black -> Array.fill(64)(Bitboard.empty)
  )

  private val knightAttacks: Array[Bitboard] = Array.fill(64)(Bitboard.empty)
  private val kingAttacks: Array[Bitboard] = Array.fill(64)(Bitboard.empty)

  // Initialize attack tables
  private def initPawnAttacks(): Unit =
    for square <- 0 until 64 do
      val sq = square.toByte
      val x = sq % 8
      val y = sq / 8

      // White pawn attacks
      if y < 7 then
        if x > 0 then pawnAttacks(Color.White)(square) = pawnAttacks(Color.White)(square).add(Square.fromIndex(x - 1 + (y + 1) * 8).get)
        if x < 7 then pawnAttacks(Color.White)(square) = pawnAttacks(Color.White)(square).add(Square.fromIndex(x + 1 + (y + 1) * 8).get)

      // Black pawn attacks
      if y > 0 then
        if x > 0 then pawnAttacks(Color.Black)(square) = pawnAttacks(Color.Black)(square).add(Square.fromIndex(x - 1 + (y - 1) * 8).get)
        if x < 7 then pawnAttacks(Color.Black)(square) = pawnAttacks(Color.Black)(square).add(Square.fromIndex(x + 1 + (y - 1) * 8).get)

  private def initKnightAttacks(): Unit =
    val knightMoves = Array((-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1))
    for square <- 0 until 64 do
      val x = square % 8
      val y = square / 8
      var attacks = Bitboard.empty
      for (dx, dy) <- knightMoves do
        val nx = x + dx
        val ny = y + dy
        if nx >= 0 && nx < 8 && ny >= 0 && ny < 8 then
          attacks = attacks.add(Square.fromIndex(nx + ny * 8).get)
      knightAttacks(square) = attacks

  private def initKingAttacks(): Unit =
    val kingMoves = Array((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1))
    for square <- 0 until 64 do
      val x = square % 8
      val y = square / 8
      var attacks = Bitboard.empty
      for (dx, dy) <- kingMoves do
        val nx = x + dx
        val ny = y + dy
        if nx >= 0 && nx < 8 && ny >= 0 && ny < 8 then
          attacks = attacks.add(Square.fromIndex(nx + ny * 8).get)
      kingAttacks(square) = attacks

  // Initialize all tables
  initPawnAttacks()
  initKnightAttacks()
  initKingAttacks()

  // Public API for piece attacks
  def getPawnAttacks(square: Square, color: Color): Bitboard =
    pawnAttacks(color)(square.index)

  def getKnightAttacks(square: Square): Bitboard =
    knightAttacks(square.index)

  def getKingAttacks(square: Square): Bitboard =
    kingAttacks(square.index)

  // Sliding piece attacks using magic bitboards or simpler approaches
  def getBishopAttacks(square: Square, occupied: Bitboard): Bitboard =
    // For now, use a simple implementation
    // In a full implementation, this would use magic bitboards
    var attacks = Bitboard.empty
    val x = square.x
    val y = square.y

    // Northeast
    var nx = x + 1
    var ny = y + 1
    while nx < 8 && ny < 8 do
      val target = Square.fromIndex(nx + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = 8 // Stop after first piece
      else
        nx += 1
        ny += 1

    // Northwest
    nx = x - 1
    ny = y + 1
    while nx >= 0 && ny < 8 do
      val target = Square.fromIndex(nx + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = -1
      else
        nx -= 1
        ny += 1

    // Southeast
    nx = x + 1
    ny = y - 1
    while nx < 8 && ny >= 0 do
      val target = Square.fromIndex(nx + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = 8
      else
        nx += 1
        ny -= 1

    // Southwest
    nx = x - 1
    ny = y - 1
    while nx >= 0 && ny >= 0 do
      val target = Square.fromIndex(nx + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = -1
      else
        nx -= 1
        ny -= 1

    attacks

  def getRookAttacks(square: Square, occupied: Bitboard): Bitboard =
    var attacks = Bitboard.empty
    val x = square.x
    val y = square.y

    // North
    var ny = y + 1
    while ny < 8 do
      val target = Square.fromIndex(x + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then ny = 8
      else ny += 1

    // South
    ny = y - 1
    while ny >= 0 do
      val target = Square.fromIndex(x + ny * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then ny = -1
      else ny -= 1

    // East
    var nx = x + 1
    while nx < 8 do
      val target = Square.fromIndex(nx + y * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = 8
      else nx += 1

    // West
    nx = x - 1
    while nx >= 0 do
      val target = Square.fromIndex(nx + y * 8).get
      attacks = attacks.add(target)
      if occupied.contains(target) then nx = -1
      else nx -= 1

    attacks

  def getQueenAttacks(square: Square, occupied: Bitboard): Bitboard =
    getBishopAttacks(square, occupied).union(getRookAttacks(square, occupied))

  // Occupancy calculations
  def getAllOccupied(world: World): Bitboard =
    world.occupancy(Color.White).union(world.occupancy(Color.Black))

  def getOccupiedByColor(world: World, color: Color): Bitboard =
    world.occupancy(color)

  def getEmptySquares(world: World): Bitboard =
    getAllOccupied(world).complement.intersection(Bitboard.fromSquares((0 until 64).map(i => Square.fromIndex(i).get): _*))

  // Check if path is clear between two squares
  def isPathClear(from: Square, to: Square, occupied: Bitboard): Boolean =
    val dx = to.x - from.x
    val dy = to.y - from.y

    if dx == 0 then // Vertical movement
      val step = if dy > 0 then 1 else -1
      var y = from.y + step
      while y != to.y do
        if occupied.contains(Square.fromIndex(from.x + y * 8).get) then return false
        y += step
    else if dy == 0 then // Horizontal movement
      val step = if dx > 0 then 1 else -1
      var x = from.x + step
      while x != to.x do
        if occupied.contains(Square.fromIndex(x + from.y * 8).get) then return false
        x += step
    else if Math.abs(dx) == Math.abs(dy) then // Diagonal movement
      val stepX = if dx > 0 then 1 else -1
      val stepY = if dy > 0 then 1 else -1
      var x = from.x + stepX
      var y = from.y + stepY
      while x != to.x && y != to.y do
        if occupied.contains(Square.fromIndex(x + y * 8).get) then return false
        x += stepX
        y += stepY

    true
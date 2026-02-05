package test

import munit.FunSuite
import pipeline.uci
import domain._

class UciParserTest extends FunSuite:

  test("UCI parser should parse 'uci' command") {
    val result = uci.parseCommand("uci")
    assert(result.isRight)
    assert(result.toOption.exists(_.isInstanceOf[uci.UciCommand.Uci.type]))
  }

  test("UCI parser should parse 'isready' command") {
    val result = uci.parseCommand("isready")
    assert(result.isRight)
    assert(result.toOption.exists(_.isInstanceOf[uci.UciCommand.IsReady.type]))
  }

  test("UCI parser should parse 'position startpos' command") {
    val result = uci.parseCommand("position startpos")
    assert(result.isRight)
    result.map {
      case cmd: uci.UciCommand.Position =>
        assert(cmd.fen.isEmpty)
        assert(cmd.moves.isEmpty)
      case _ => fail("Expected Position command")
    }
  }

  test("UCI parser should parse 'position startpos moves e2e4'") {
    val result = uci.parseCommand("position startpos moves e2e4 c7c5")
    assert(result.isRight)
    result.map {
      case cmd: uci.UciCommand.Position =>
        assert(cmd.fen.isEmpty)
        assert(cmd.moves == List("e2e4", "c7c5"))
      case _ => fail("Expected Position command")
    }
  }

  test("UCI parser should parse 'go depth 6' command") {
    val result = uci.parseCommand("go depth 6")
    assert(result.isRight)
    result.map {
      case cmd: uci.UciCommand.Go =>
        assert(cmd.depth == Some(6))
      case _ => fail("Expected Go command")
    }
  }

  test("UCI parser should parse 'quit' command") {
    val result = uci.parseCommand("quit")
    assert(result.isRight)
    assert(result.toOption.exists(_.isInstanceOf[uci.UciCommand.Quit]))
  }

  test("UCI response formatter should format Id") {
    val response = uci.formatResponse(uci.UciResponse.Id)
    assert(response.contains("id name"))
    assert(response.contains("id author"))
  }

  test("UCI response formatter should format UciOk") {
    val response = uci.formatResponse(uci.UciResponse.UciOk)
    assert(response == "uciok")
  }

  test("UCI response formatter should format ReadyOk") {
    val response = uci.formatResponse(uci.UciResponse.ReadyOk)
    assert(response == "readyok")
  }

  test("UCI response formatter should format BestMove") {
    val response = uci.formatResponse(uci.UciResponse.BestMove("e2e4"))
    assert(response == "bestmove e2e4")
  }

  test("UCI response formatter should format BestMove with ponder") {
    val response = uci.formatResponse(uci.UciResponse.BestMove("e2e4", Some("c7c5")))
    assert(response == "bestmove e2e4 ponder c7c5")
  }

  test("moveToUci should convert move intent to UCI notation") {
    val move = MoveIntent(Position(4, 1), Position(4, 3), None)  // e2e4
    val uciStr = uci.moveToUci(move)
    assert(uciStr == "e2e4")
  }

  test("moveToUci should include promotion in UCI notation") {
    val move = MoveIntent(Position(0, 6), Position(0, 7), Some(PieceType.Queen))  // a7a8q
    val uciStr = uci.moveToUci(move)
    assert(uciStr == "a7a8q")
  }

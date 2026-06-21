//> using scala 3
//> using dep "org.creativescala::doodle:0.34.0"

import cats.effect.{IO, IOApp}
import cats.effect.unsafe.implicits.global
import doodle.core.*
import doodle.core.font.*
import doodle.image.*
import doodle.image.syntax.all.*
import doodle.java2d.*
import doodle.reactor.*
import doodle.reactor.syntax.all.*
import doodle.syntax.all.*
import scala.concurrent.duration.*

// -----------------------------------------------------------------------------
// Domain model
// -----------------------------------------------------------------------------

final case class Cell(row: Int, col: Int)

final case class BoardSize(rows: Int, cols: Int) {
  require(rows > 0, "A board must have at least one row")
  require(cols > 0, "A board must have at least one column")

  def contains(cell: Cell): Boolean =
    cell.row >= 0 && cell.row < rows &&
      cell.col >= 0 && cell.col < cols

  def cells: IndexedSeq[Cell] =
    for {
      row <- 0 until rows
      col <- 0 until cols
    } yield Cell(row, col)
}

final case class Board(size: BoardSize, alive: Set[Cell]) {
  def contains(cell: Cell): Boolean =
    alive.contains(cell)

  def toggle(cell: Cell): Board =
    if !size.contains(cell) then this
    else if contains(cell) then copy(alive = alive - cell)
    else copy(alive = alive + cell)

  def advance: Board = {
    val nextAlive =
      size.cells.iterator
        .filter { cell =>
          val neighbors = liveNeighborCount(cell)

          if contains(cell) then
            neighbors == 2 || neighbors == 3
          else
            neighbors == 3
        }
        .toSet

    copy(alive = nextAlive)
  }

  private def liveNeighborCount(cell: Cell): Int =
    Board.neighborOffsets.count { case (dRow, dCol) =>
      val neighbor = wrap(Cell(cell.row + dRow, cell.col + dCol))
      contains(neighbor)
    }

  private def wrap(cell: Cell): Cell =
    Cell(
      row = Math.floorMod(cell.row, size.rows),
      col = Math.floorMod(cell.col, size.cols)
    )
}

object Board {
  val neighborOffsets: Vector[(Int, Int)] =
    (for {
      dRow <- -1 to 1
      dCol <- -1 to 1
      if dRow != 0 || dCol != 0
    } yield (dRow, dCol)).toVector

  def empty(size: BoardSize): Board =
    Board(size, Set.empty)

  def glider(size: BoardSize): Board =
    Board(
      size,
      Set(
        Cell(1, 2),
        Cell(2, 3),
        Cell(3, 1),
        Cell(3, 2),
        Cell(3, 3)
      ).filter(size.contains)
    )

  def random(size: BoardSize, density: Double, rng: Rng): (Rng, Board) = {
    val probability = density.max(0.0).min(1.0)

    val (nextRng, alive) =
      size.cells.foldLeft((rng, Set.empty[Cell])) {
        case ((currentRng, currentAlive), cell) =>
          val (newRng, value) = currentRng.nextDouble
          val newAlive =
            if value < probability then currentAlive + cell
            else currentAlive

          (newRng, newAlive)
      }

    (nextRng, Board(size, alive))
  }
}

// -----------------------------------------------------------------------------
// Pure random number generator
// -----------------------------------------------------------------------------

final case class Rng(seed: Long) {
  def nextDouble: (Rng, Double) = {
    val nextSeed = seed * 6364136223846793005L + 1442695040888963407L
    val value = (nextSeed >>> 11).toDouble / (1L << 53).toDouble
    (Rng(nextSeed), value)
  }
}

// -----------------------------------------------------------------------------
// Application model and messages
// -----------------------------------------------------------------------------

final case class Model(
  board: Board,
  isPaused: Boolean,
  rng: Rng
) {
  def tick: Model =
    if isPaused then this
    else copy(board = board.advance)
}

enum Msg {
  case TogglePaused
  case Tick
  case StepOnce
  case RandomizeBoard
  case ClearBoard
  case ToggleCell(cell: Cell)
}

final case class Config(
  boardSize: BoardSize,
  layout: Layout,
  randomDensity: Double,
  tickRate: FiniteDuration
)

object Config {
  val default: Config =
    Config(
      boardSize = BoardSize(rows = 30, cols = 40),
      layout = Layout.default,
      randomDensity = 0.3,
      tickRate = 200.milliseconds
    )
}

// Pure update: same message + same model + same config => same next model.
def update(config: Config)(msg: Msg, model: Model): Model =
  msg match {
    case Msg.TogglePaused =>
      model.copy(isPaused = !model.isPaused)

    case Msg.Tick =>
      model.tick

    case Msg.StepOnce =>
      model.copy(
        board = model.board.advance,
        isPaused = true
      )

    case Msg.RandomizeBoard =>
      val (nextRng, randomBoard) =
        Board.random(model.board.size, config.randomDensity, model.rng)

      model.copy(
        board = randomBoard,
        rng = nextRng
      )

    case Msg.ClearBoard =>
      model.copy(
        board = Board.empty(model.board.size),
        isPaused = true
      )

    case Msg.ToggleCell(cell) =>
      model.copy(board = model.board.toggle(cell))
  }

// -----------------------------------------------------------------------------
// Layout and UI data
// -----------------------------------------------------------------------------

final case class Layout(
  cellSize: Double,
  backgroundPaddingX: Double,
  backgroundExtraHeight: Double,
  buttonWidth: Double,
  buttonHeight: Double,
  buttonSpacing: Double
) {
  def boardWidth(size: BoardSize): Double =
    size.cols * cellSize

  def boardHeight(size: BoardSize): Double =
    size.rows * cellSize

  def titleY(size: BoardSize): Double =
    boardHeight(size) / 2.0 + 35.0

  def subtitleY(size: BoardSize): Double =
    boardHeight(size) / 2.0 + 15.0

  def controlsY(size: BoardSize): Double =
    -boardHeight(size) / 2.0 - 35.0

  def backgroundWidth(size: BoardSize): Double =
    boardWidth(size) + backgroundPaddingX

  def backgroundHeight(size: BoardSize): Double =
    boardHeight(size) + backgroundExtraHeight

  def frameWidth(size: BoardSize): Int =
    (backgroundWidth(size) + 10.0).ceil.toInt

  def frameHeight(size: BoardSize): Int =
    (backgroundHeight(size) + 10.0).ceil.toInt

  def cellCenter(size: BoardSize, cell: Cell): (Double, Double) = {
    val x = (cell.col - (size.cols - 1) / 2.0) * cellSize
    val y = ((size.rows - 1) / 2.0 - cell.row) * cellSize
    (x, y)
  }

  def pointToCell(size: BoardSize, point: Point): Option[Cell] = {
    val col = Math.round(point.x / cellSize + (size.cols - 1) / 2.0).toInt
    val row = Math.round((size.rows - 1) / 2.0 - point.y / cellSize).toInt
    val cell = Cell(row, col)

    Option.when(size.contains(cell))(cell)
  }

  def buttonCenterXs(buttonCount: Int): List[Double] = {
    val totalWidth = buttonCount * buttonWidth + (buttonCount - 1) * buttonSpacing
    val firstCenter = -totalWidth / 2.0 + buttonWidth / 2.0

    List.tabulate(buttonCount)(index => firstCenter + index * (buttonWidth + buttonSpacing))
  }
}

object Layout {
  val default: Layout =
    Layout(
      cellSize = 15.0,
      backgroundPaddingX = 40.0,
      backgroundExtraHeight = 140.0,
      buttonWidth = 80.0,
      buttonHeight = 30.0,
      buttonSpacing = 20.0
    )
}

final case class Rect(
  centerX: Double,
  centerY: Double,
  width: Double,
  height: Double
) {
  def contains(point: Point): Boolean =
    point.x >= centerX - width / 2.0 &&
      point.x <= centerX + width / 2.0 &&
      point.y >= centerY - height / 2.0 &&
      point.y <= centerY + height / 2.0
}

final case class Button(
  label: String,
  bounds: Rect,
  msg: Msg
) {
  def contains(point: Point): Boolean =
    bounds.contains(point)
}

// -----------------------------------------------------------------------------
// Pure input decoding
// -----------------------------------------------------------------------------

def buttons(config: Config, model: Model): List[Button] = {
  val labelsAndMessages = List(
    (if model.isPaused then "PLAY" else "PAUSE", Msg.TogglePaused),
    ("STEP", Msg.StepOnce),
    ("RANDOM", Msg.RandomizeBoard),
    ("CLEAR", Msg.ClearBoard)
  )

  val layout = config.layout
  val y = layout.controlsY(config.boardSize)
  val xs = layout.buttonCenterXs(labelsAndMessages.length)

  labelsAndMessages.zip(xs).map { case ((label, msg), x) =>
    Button(
      label = label,
      bounds = Rect(
        centerX = x,
        centerY = y,
        width = layout.buttonWidth,
        height = layout.buttonHeight
      ),
      msg = msg
    )
  }
}

def mouseClickToMsg(config: Config)(point: Point, model: Model): Option[Msg] =
  buttons(config, model)
    .find(_.contains(point))
    .map(_.msg)
    .orElse {
      config.layout
        .pointToCell(model.board.size, point)
        .map(Msg.ToggleCell.apply)
    }

// -----------------------------------------------------------------------------
// Pure rendering
// -----------------------------------------------------------------------------

def render(config: Config)(model: Model): Image =
  renderTitle(config) on
    renderSubtitle(config) on
    renderBoard(config, model.board) on
    renderButtons(config, model) on
    renderBackground(config)

def renderTitle(config: Config): Image =
  Image.text("CONWAY'S GAME OF LIFE")
    .font(Font.defaultSansSerif.withSize(FontSize.points(18)).withBold)
    .fillColor(Color.black)
    .at(0, config.layout.titleY(config.boardSize))

def renderSubtitle(config: Config): Image =
  Image.text("Click cells to flip. Control with buttons below.")
    .font(Font.defaultSansSerif.withSize(FontSize.points(11)))
    .fillColor(Color.darkGray)
    .at(0, config.layout.subtitleY(config.boardSize))

def renderBoard(config: Config, board: Board): Image =
  combineImages(
    board.size.cells.map(cell => renderCell(config, board, cell))
  )

def renderCell(config: Config, board: Board, cell: Cell): Image = {
  val layout = config.layout
  val (x, y) = layout.cellCenter(board.size, cell)

  val cellColor =
    if board.contains(cell) then Color.black
    else Color.white

  Image.rectangle(layout.cellSize - 1.0, layout.cellSize - 1.0)
    .fillColor(cellColor)
    .strokeColor(Color.gray)
    .strokeWidth(1.0)
    .at(x, y)
}

def renderButtons(config: Config, model: Model): Image =
  combineImages(
    buttons(config, model).map(renderButton)
  )

def renderButton(button: Button): Image = {
  val box = Image.rectangle(button.bounds.width, button.bounds.height)
    .fillColor(Color.white)
    .strokeColor(Color.black)
    .strokeWidth(1.5)

  val text = Image.text(button.label)
    .font(Font.defaultSansSerif.withSize(FontSize.points(12)))
    .fillColor(Color.black)

  text.on(box).at(button.bounds.centerX, button.bounds.centerY)
}

def renderBackground(config: Config): Image = {
  val size = config.boardSize
  val layout = config.layout

  Image.rectangle(
    layout.backgroundWidth(size),
    layout.backgroundHeight(size)
  )
    .fillColor(Color.white)
    .strokeColor(Color.black)
    .strokeWidth(2.0)
}

def combineImages(images: Iterable[Image]): Image = {
  val indexed = images.toIndexedSeq

  def loop(slice: IndexedSeq[Image]): Image =
    if slice.isEmpty then Image.empty
    else if slice.length == 1 then slice.head
    else {
      val (left, right) = slice.splitAt(slice.length / 2)
      loop(left) on loop(right)
    }

  loop(indexed)
}

// -----------------------------------------------------------------------------
// Effect boundary
// -----------------------------------------------------------------------------

object GameOfLife extends IOApp.Simple {
  override def run: IO[Unit] = {
    val config = Config.default

    for {
      seed <- IO.realTime.map(_.toNanos)
      initialModel = Model(
        board = Board.glider(config.boardSize),
        isPaused = true,
        rng = Rng(seed)
      )
      frame = Frame.default
        .withSize(
          config.layout.frameWidth(config.boardSize),
          config.layout.frameHeight(config.boardSize)
        )
        .withTitle("Conway's Game of Life")
        .withCenterAtOrigin
      reactor = Reactor.init(initialModel)
        .withOnTick(model => update(config)(Msg.Tick, model))
        .withTickRate(config.tickRate)
        .withRender(render(config))
        .withOnMouseClick { (point, model) =>
          mouseClickToMsg(config)(point, model)
            .fold(model)(msg => update(config)(msg, model))
        }
      _ <- frame.canvas().use { canvas =>
        IO.race(
          reactor.animateWithCanvasToIO(canvas),
          canvas.closed
        ).map(_ => ())
      }
    } yield ()
  }
}

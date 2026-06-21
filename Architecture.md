# Architecture

This document explains the core design decisions, data structures, and code organization of the ScaGOL (Scala Game of Life) project.

## Core Design Decisions

1. **Pure Functional Core, Explicit Effectful Shell**
   The application is designed around a pure functional core. Domain logic, state transitions, input decoding, and rendering descriptions are expressed as pure functions over immutable values. The full program still performs real-world effects: it opens a window, receives mouse clicks, listens to clock ticks, and shuts down. Those effects are isolated at the edge of the program in `GameOfLife extends IOApp.Simple`.

   The architectural rule is:

   > The core is pure. The runtime shell is effectful.

   The code interacts with the outside world through a small, explicit Cats Effect boundary.

2. **The Elm Architecture (MVU)**
   The project follows the **Model-View-Update** pattern:
   - **Model**: The `Model` case class holds the dynamic state of the application: the `Board`, the playback state, and the deterministic `Rng` state.
   - **View**: The `render` functions purely project the current `Model` and static `Config` into a visual `Image`.
   - **Update**: The `update` function handles all transitions, processing discrete `Msg` events to generate the next `Model`.

   This is an Elm-inspired architecture adapted to Scala and Doodle. We do not model Elm-style `Cmd` values directly; instead, external effects are wired at the runtime boundary and converted into messages handled by the pure update loop.

3. **Doodle Graphics Library**
   We use [Doodle](https://github.com/creativescala/doodle), a functional 2D vector graphics library for Scala. Instead of drawing pixels imperatively to a canvas, we construct complex visuals by algebraically composing simpler `Image` primitives using combinators like `on` (stacking) and `at` (translating). The interactive loop is driven by Doodle's `Reactor`.

4. **Deterministic Randomness**
   Random board generation is modeled without calling `scala.util.Random` inside `update`. Instead, the application stores a small deterministic `Rng` value inside the `Model` and threads it through random board generation.

   This preserves the main MVU property: given the same `Config`, `Msg`, and `Model`, `update` always returns the same next `Model`. Randomization is therefore reproducible, testable, and compatible with the rest of the architecture.

---

## Key Algebraic Data Types (ADTs)

- **`Cell`**
  A strongly typed coordinate, `Cell(row, col)`, used instead of raw `(Int, Int)` tuples. This prevents tuple-blindness and makes row/column ordering explicit.

- **`BoardSize`**
  A strongly typed representation of board dimensions. It validates that the board has at least one row and one column, exposes all valid cells, and centralizes coordinate bounds checking.

- **`Board` (The Domain)**
  Encapsulates the rules of Conway's Game of Life. It tracks live cells as `Set[Cell]`, computes live neighbor counts, applies survival/birth rules, toggles cells, and handles toroidal wrapping.

- **`Model` (The App State)**
  Holds the dynamic application state: the `Board`, the playback state (`isPaused`), and the deterministic `Rng` seed for randomization. It does not hold static layout or timing configuration.

- **`Config` (The Environment)**
  A static configuration object holding `BoardSize`, `Layout`, `randomDensity`, and `tickRate`. This is curried into pure functions such as `update`, `render`, and `mouseClickToMsg`, making dependencies explicit without storing them in the dynamic model.

- **`Msg` (The Events)**
  A Scala 3 `enum` representing every possible event in the application:
  - `TogglePaused`, `StepOnce`, `RandomizeBoard`, `ClearBoard`: triggered by UI buttons.
  - `ToggleCell(cell)`: triggered by clicking the grid.
  - `Tick`: emitted by the system clock.

- **`Layout`**
  A pure data structure holding visual constants and coordinate conversion logic: cell size, button dimensions, background dimensions, frame size, and the mapping between Doodle `Point`s and logical `Cell`s.

- **`Button` & `Rect`**
  Pure data components representing clickable bounding boxes and their associated messages.

- **`Rng`**
  A small deterministic random number generator. It returns both the next RNG value and the generated value, making randomness explicit and referentially transparent.

---

## Domain Invariants

The code uses small domain-specific types to make illegal or confusing states harder to express.

- `Cell(row, col)` makes coordinates explicit.
- `BoardSize(rows, cols)` ensures board dimensions are positive.
- `Board(size, alive)` attaches Conway's rules to the data they govern.
- `Config` separates static environmental settings from dynamic application state.
- `Model` contains only state that can evolve during the application.

This separation matters because Conway's Game of Life has a simple rule set, but the application around it has several different concepts: simulation state, UI layout, event decoding, rendering, randomness, and runtime execution. Keeping these concepts separate prevents the code from turning into a soup of coordinates, booleans, and magic numbers.

---

## Key Functions

- **`Board.advance: Board`**
  Encapsulates the core Conway's Game of Life logic. It calculates the next generation by evaluating the survival and birth rules for every cell on the board.

- **`Board.random(size: BoardSize, density: Double, rng: Rng): (Rng, Board)`**
  Builds a randomized board while explicitly threading the RNG state. This function is pure: the same size, density, and RNG always produce the same next RNG and board.

- **`Model.tick: Model`**
  Advances the board only when the model is not paused. This keeps the rule "ticks do nothing while paused" close to the application state it governs.

- **`update(config: Config)(msg: Msg, model: Model): Model`**
  The centralized pure MVU transition function. It pattern matches on the incoming `Msg` and returns the next `Model`, delegating to domain operations such as `board.advance`, `board.toggle`, and `Board.random`.

- **`mouseClickToMsg(config: Config)(point: Point, model: Model): Option[Msg]`**
  A pure event decoder. It maps raw Doodle mouse coordinates into either a button message or a grid-cell message. If the click does not correspond to an interactive region, it returns `None`.

- **`render(config: Config)(model: Model): Image`**
  The View function. It delegates to smaller composable pure functions such as `renderBoard`, `renderCell`, `renderButtons`, and `renderBackground` to map the current state into a Doodle `Image`.

- **`combineImages(images: Iterable[Image]): Image`**
  Combines many image primitives using a balanced binary tree. This avoids constructing a deeply skewed image AST, which can otherwise trigger a `StackOverflowError` when rendering many cells.

---

## Code Walkthrough

If you are a developer reading the codebase for the first time, follow this sequence to understand how the pieces fit together.

### 1. The Domain (`Cell`, `BoardSize`, and `Board`)
Start with `Cell` and `BoardSize`, then read `Board`. Understand that `alive: Set[Cell]` represents the living cells. Look at `advance`, `liveNeighborCount`, and `wrap` to see Conway's rules and the toroidal board behavior.

### 2. The Application State (`Model`, `Config`, and `Rng`)
Read `Model` to see what changes during execution. Read `Config` to see what is static. Read `Rng` to understand how randomization remains deterministic and pure.

### 3. The Vocabulary (`Msg`)
Read the `enum Msg`. This defines the entire language of interactions for the app. A useful MVU rule of thumb is: if something meaningful can happen in the application, it should probably be represented as a `Msg`.

### 4. The Logic (`update`)
Read the `update` function. See how every `Msg` translates into a specific transformation of the `Model`. Notice that even `RandomizeBoard` is pure, because it threads the RNG through the model rather than creating a side effect.

### 5. The Input Decoder (`mouseClickToMsg`)
Read `mouseClickToMsg` after `update`. This function turns raw mouse coordinates into meaningful application messages. It is the bridge between the graphical coordinate system and the domain-level event vocabulary.

### 6. The View (`render` & `combineImages`)
Read the pure `render*` functions. Notice how they build the UI from `Config` and `Model`. Pay special attention to `combineImages`, which balances the graphics AST so rendering remains stack-safe.

### 7. The Runtime (`GameOfLife extends IOApp.Simple`)
Finally, look at the bottom of the program. This is where everything is wired together using Doodle's `Reactor` inside a Cats Effect `IOApp`.

- It creates the static `Config`.
- It obtains an initial seed effectfully from the clock.
- It initializes the `Model`.
- It registers the pure `update`, `render`, and `mouseClickToMsg` functions with the reactor.
- It extracts a `canvas` resource from the frame.
- It races `reactor.animateWithCanvasToIO(canvas)` against `canvas.closed`, ensuring graceful shutdown when the window closes.

---

## Purity Boundary

The pure core includes:

- the domain model (`Cell`, `BoardSize`, `Board`),
- deterministic randomness (`Rng`),
- application state (`Model`),
- messages (`Msg`),
- state transitions (`update`),
- input decoding (`mouseClickToMsg`), and
- rendering descriptions (`render` and its helper functions).

The effectful shell includes:

- reading the initial time to seed the RNG,
- opening the window,
- receiving mouse and tick events from Doodle,
- running the reactor loop, and
- responding to the window closing.

This makes the effect boundary explicit. The outside world is allowed to touch the program, but only at the edge.

---

## Trade-offs & Known Limitations

- **Rendering Depth (`combineImages` vs `foldLeft`)**
  Doodle builds visuals by wrapping `Image`s into an Abstract Syntax Tree (AST). Using `foldLeft` to stack thousands of cell rectangles creates a deeply skewed, unbalanced tree, which can cause a `StackOverflowError` on the JVM during rendering. To prevent this, `combineImages` recursively splits the sequence of images in half, producing a balanced AST that renders more safely.

- **Data Structure Choice (`Set[Cell]`)**
  The board stores living cells as a `Set[Cell]`. This is clear, immutable, and memory-efficient for sparse boards. It also makes membership checks simple and fast. However, the current `Board.advance` implementation still evaluates every cell on the finite board each generation. For a massive board, more specialized representations could be faster, such as neighbor-count maps for sparse infinite boards or flat arrays/bitsets for dense finite boards.

- **Toroidal Board**
  The board wraps at the edges using `Math.floorMod`, making it topologically equivalent to a torus. This avoids edge special cases and keeps the rules uniform, but it differs from an infinite Game of Life universe.

- **No Elm-style Commands**
  The architecture is MVU-inspired, but it does not implement Elm's `Cmd` abstraction directly. Runtime effects are handled by Cats Effect and Doodle's Reactor wiring rather than being represented as first-class command values returned from `update`.

- **Rendering Performance**
  The implementation prioritizes clarity, purity, and teachability over raw rendering performance. Rendering every cell as an individual vector rectangle is elegant and easy to reason about, but it may not be the fastest approach for very large boards.

---

## Testing Strategy

A major benefit of the MVU architecture is its testability. Because the domain logic, event decoding, rendering descriptions, deterministic randomization, and `update` function are decoupled from Doodle's runtime loop, we can write fast, headless unit tests for most of the application.

Examples of useful tests:

- `Board.advance` preserves a stable block pattern.
- `Board.advance` turns a vertical blinker into a horizontal blinker, then back again.
- `Board.toggle` adds a dead cell and removes a live cell.
- `Model.tick` does nothing while paused.
- `update(config)(Msg.StepOnce, model)` advances one generation and pauses.
- `update(config)(Msg.RandomizeBoard, model)` is deterministic for a fixed RNG seed.
- `mouseClickToMsg` maps button clicks to the expected `Msg` values.
- `mouseClickToMsg` maps board clicks to `Msg.ToggleCell(cell)`.

For example, using ScalaTest or MUnit, a test can initialize a `Model`, call `update(config)(Msg.StepOnce, model)`, and assert that the resulting `Set[Cell]` matches the expected coordinates without ever opening a graphics window.

---

## Coordinate System Mapping

Doodle renders pure vector graphics on a canvas rather than HTML DOM nodes, so we cannot attach native `onClick` event listeners directly to a drawn cell or button.

Instead, the `Reactor` captures global mouse clicks as abstract geometric `Point(x, y)` values. The pure input decoder maps those points back into domain-level events:

- If the point is inside a button's `Rect`, it emits the button's associated `Msg`.
- Otherwise, if the point maps onto a valid board coordinate, it emits `Msg.ToggleCell(cell)`.
- Otherwise, it emits no message.

The `Layout` object owns this coordinate conversion. This keeps the geometry in one place and prevents UI math from leaking into the update function.

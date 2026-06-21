# Scala Game of Life (ScaGOL)

> **Note:** This is a purely "vibe-coded" and AI-reviewed project created for self-educational purposes by **Michalis Famelis**  
> Email: [famelis@iro.umontreal.ca](mailto:famelis@iro.umontreal.ca) | Website: [http://www.iro.umontreal.ca/~famelis/](http://www.iro.umontreal.ca/~famelis/)

A fully interactive, pure functional implementation of [Conway's Game of Life](https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life) built in Scala 3 using the [Doodle](https://github.com/creativescala/doodle) vector graphics library.

## Documentation

- **[Architecture.md](Architecture.md)**: Explains the core design decisions, algebraic data types, domain invariants, and provides a walkthrough of the purely functional Elm-inspired architecture.
- **[VIBE_LOG.md](VIBE_LOG.md)**: A chronological "vibe-coding" log detailing the history of the prompts, AI interactions, debugging sessions, and multi-model code reviews that built the application.

## Features
- **Pure Functional Core, Explicit Effectful Shell**: Domain logic and state transitions are 100% pure, while windowing and clock interactions are safely pushed to the edges via Cats Effect's `IOApp.Simple`.
- **The Elm Architecture (MVU)**: Strict separation of concerns across `Model` (dynamic state), `View` (pure functions returning Doodle `Image` trees), and `Update` (pure state transitions via `Msg`).
- **Deterministic Randomness**: Random board generation avoids `scala.util.Random` side effects by threading an explicit, pure `Rng` state through the updates.
- **Interactive UI**: A built-in control panel for playback and a clickable grid with a high-contrast aesthetic.

## Installation

### Prerequisites
You need [Scala CLI](https://scala-cli.virtuslab.org/) installed to run this project.

## Execution

Because this project uses Scala CLI directives (`//> using scala 3` and `//> using dep ...`), all dependencies (like Doodle and Cats Effect) are fetched automatically.

To launch the interactive GUI, simply run:
```bash
scala-cli run GameOfLife.scala
```

### Controls
Once the window opens, you can interact with the simulation:
- **Click the Grid**: Toggle individual cells between alive and dead states.
- **PLAY / PAUSE**: Start or stop the automatic generation timer.
- **STEP**: Advance the simulation manually by exactly one generation.
- **RANDOM**: Randomize the grid with a 30% chance of a cell being alive.
- **CLEAR**: Wipe the board completely clean, allowing you to draw custom patterns from scratch.

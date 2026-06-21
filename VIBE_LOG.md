# Vibe Coding Log

This project was built and refined almost entirely via **"vibe coding"**—steering an AI assistant through natural language prompts rather than writing code manually. 

This document serves as an artifact of the prompt journey, showcasing how the project evolved from a failing state into a robust, purely functional application.

## The Prompt Journey

### Phase 1: Getting it to Run & Debugging
The project started with an existing, but broken, implementation.

1. > *"This project contains a scala implementation of the game of life using the doodle library for vizualization. How do I run it?"*
   - **Result:** The AI attempted to run the code using `scala-cli run GameOfLife.scala`.
2. > *"It throws an exception"*
   - **Result:** The AI diagnosed a `StackOverflowError` occurring deep within the Doodle graphics library caused by `foldLeft` building an unbalanced Abstract Syntax Tree. It fixed this by implementing a divide-and-conquer `treeFold` algorithm to balance the tree.

### Phase 2: Aesthetics & UX
Once it ran, it wasn't very usable.

3. > *"the background of the application is dark and I can't see the letters"*
   - **Result:** The AI rewrote the rendering logic to use a high-contrast, clean light theme (black cells on white background with a dark gray header).
4. > *"good i can see it now. But mouse clicks don't do anything"*
   - **Result:** The AI investigated Doodle's `Reactor` API, ran several internal tests to verify the version, and discovered that `.draw(frame)` only evaluated a static frame. It replaced it with `.animateWithFrame(frame)` to properly engage the interactive event loop.

### Phase 3: Architecture & Refactoring
With the app functioning, the focus shifted to code quality and architectural purity.

5. > *"Great. Now, I want you to consider what would be required to transform the project into using the Elm Architecture (Model-View-Update)"*
   - **Result:** The AI generated a detailed implementation plan, proposing a Scala 3 `enum Msg`, a pure `update` function, and decoupled UI components. Once approved, it refactored the entire file to adhere to strict MVU principles.

### Phase 4: Documentation & Cleanup
Finally, the project needed to be documented for human developers.

6. > *"great! Either clear up all the test files you created, or put them in a test folder if they are absolutely necessary"*
   - **Result:** The AI deleted all the temporary `test_*.scala` scratchpads used during debugging.
7. > *"do we need test_gol.scala?"*
   - **Result:** The AI explained it was just an optional headless correctness test, and deleted it to keep the repository minimalist.
8. > *"Good. Now turn the file walkthrough.md into a proper README.md that explains installation and execution instructions"*
   - **Result:** `README.md` was created, providing standard setup and execution steps.
9. > *"Write an Architecture.md file. It should explain the core design decisions of the program (e.g., 100% functional scala, doodle, Elm arch)..."*
   - **Result:** The AI documented the internal design, ADTs, and provided a 5-step code reading guide.
10. > *"Is there anything else the Architecture.md should include?"* / *"go for it"*
    - **Result:** The AI proactively appended sections on the trade-offs of the chosen data structures, testing strategies, and the math behind mapping global coordinate clicks into grid indices.

### Phase 5: Window Lifecycle & Idiomatic Refactoring
The application worked, but the JVM hung in the terminal after closing the UI window.

11. > *"when I close the window, the program does not terminate"*
    - **Result:** The AI noticed Doodle's `Reactor` evaluates as an infinite `fs2` stream blocking the main thread. It initially added `sys.exit(0)` at the end of the method, which failed because the infinite stream never returned. It then added a daemon thread polling AWT `Window.getWindows()`, which worked but was hacky.
12. > *"isn't that a bit overkill?"*
    - **Result:** The AI agreed the polling loop was overkill and replaced it with a simpler one-time `java.util.Timer` hooking an AWT `WindowListener` to trigger `sys.exit(0)`.
13. > *"Please simplify the window-closing logic... Instead, use Doodle’s intended lifecycle APIs... Also clean up the Game of Life update logic."*
    - **Result:** The user provided the pure Cats Effect architectural shape. The AI successfully dropped all Java AWT hacks, wrapping the execution in a `frame.canvas().use` resource block, racing the infinite animation fiber against the `canvas.closed` event. It also pulled duplicated game logic into a centralized `State.advance` method.
55. > *"cleanup the test_*.scala files"*
    - **Result:** All temporary scripts generated while debugging Cats Effect were deleted.

### Phase 6: Strict Elm Architecture & Code Review
An external code review (in `code-review.md`) highlighted that the architecture, while functionally separated, still bled concerns (e.g. tuples instead of domain objects, hardcoded layouts, impure randomness in the update loop).

15. > *"The file @[code-review.md] contains a detailed code review. Act on it."*
    - **Result:** The AI drafted a comprehensive refactoring plan and completely rebuilt the file. It introduced strongly-typed `Cell` and `Board` models, a `Layout` config, and a purely deterministic `Rng` state to remove all side-effects from the `update` loop. It extracted a pure `mouseClickToMsg` event decoder and broke the monolithic `render` into semantic components. Finally, it wrapped the entire execution in an idiomatic `IOApp.Simple`.

### Phase 7: Architectural Comparison & Adoption
The user generated an alternative implementation via ChatGPT (`GOL.scala`) based on the same code review and asked the AI to fix its compilation errors and compare the two files.

16. > *"ChatGPT produced the file GOL.scala. But I can't run it. Can you fix it? Afterwards, compare your version with the corrected GOL.scala"*
    - **Result:** The AI fixed Markdown formatting and implicit `IORuntime` compiler errors. It then generated a detailed `code_comparison.md` analyzing both files. It concluded that while both achieved the pure Elm architecture, ChatGPT's version was slightly more elegant due to curried `Config` dependency injection and superior domain encapsulation (`Board.random`).
17. > *"Thank you for your objectiveness and humility! In this case, let's keep ChatGPTs version and forget the other one."*
    - **Result:** The AI replaced its own `GameOfLife.scala` with the superior `GOL.scala`, deleted the review files, and synced the documentation to reflect the final adopted architecture.

### Phase 8: Open Source Licensing
18. > *"Add an Apache-2.0 license."*
    - **Result:** The AI generated a standard `LICENSE` file containing the Apache 2.0 terms.

## Takeaways
- The AI was highly capable of diagnosing obscure JVM/library-specific errors (like `StackOverflowError` in a vector graphics AST) based simply on "It throws an exception."
- The AI can act as an architectural partner, easily mapping higher-level conceptual patterns (like Elm's MVU) onto non-native paradigms (like Doodle's `Reactor`).
- Keeping an explicit history of "prompts" serves as excellent living documentation for *why* the codebase looks the way it does.
- **Multi-AI Collaboration**: "Vibe coding" isn't restricted to a single AI assistant. Using a different model (like ChatGPT) to perform a rigorous code review and generate an alternative implementation, then bringing that code back to the primary AI assistant for debugging, comparison, and integration, is a highly effective workflow. The AI can demonstrate humility and objectiveness, actively adopting "competing" code if it proves structurally superior.

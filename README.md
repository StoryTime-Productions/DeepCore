<p align="center">
  <img width="1156" height="430" alt="DeepCore banner" src="https://github.com/user-attachments/assets/c338d277-2d2d-4b74-9a04-4c2dbd4b8f5b" />
</p>

<p align="center">
  <a href="https://github.com/StoryTime-Productions/DeepCore/actions/workflows/ci.yml"><img src="https://github.com/StoryTime-Productions/DeepCore/actions/workflows/ci.yml/badge.svg" alt="CI status" /></a>
  <img src="https://img.shields.io/badge/Paper-1.21.10-blue" alt="Paper 1.21.10" />
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21" />
</p>

# DeepCore

DeepCore is a Paper plugin for running collaborative Minecraft speedruns with toggleable mechanics. Players configure a shared or per-player rule set (keep inventory, hardcore, shared health, degrading inventory, and more) through an in-game prep GUI, then run the challenge in a generated world with results tracked in SQLite.

## What It Does

- Prep GUI for challenge setup before each run
- Presets plus per-mechanic toggles
- Shared and individual gameplay modifiers (health, inventory, hardcore, and more)
- Run records storage via SQLite
- World reset workflow between runs
- Training gym with practice challenges (portal, craft, chest, bridge) for solo/team drilling

### Prep Flow (Player Experience)

1. Every online player receives the DeepCore prep book.
2. Right-click the book to open the prep GUI.
3. Configure preset and mechanic toggles.
4. Each player marks themselves ready.
5. When everyone is ready, countdown starts and settings lock.
6. Run begins in the generated challenge world.

### Implemented Mechanic Toggles

- keep_inventory
- unlimited_deaths
- hardcore
- health_refill
- shared_inventory
- shared_health
- initial_half_heart
- degrading_inventory

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/challenge status` | Show current challenge status | `deepcore.challenge` |
| `/challenge list` | List available challenge modes | `deepcore.challenge` |
| `/challenge enable` / `disable` | Enable or disable the challenge | `deepcore.challenge.admin` |
| `/challenge mode <mode-key>` | Switch challenge mode | `deepcore.challenge.admin` |
| `/challenge component list` / `status` | List or show mechanic toggle components | `deepcore.challenge` |
| `/challenge component <component-key> <on\|off\|toggle>` | Toggle a mechanic component | `deepcore.challenge.admin` |
| `/challenge reset` / `resetworld` | Reset overworld, nether, and end via limbo | `deepcore.challenge.reset` |
| `/challenge end` | End the current run and return to prep | `deepcore.challenge.end` |
| `/challenge pause` / `resume` | Pause or resume an active run | `deepcore.challenge.pause` |
| `/challenge reload` | Reload DeepCore config from disk | `deepcore.challenge.reload` |
| `/challenge logs` | Manage own DeepCore log preferences | `deepcore.challenge` |
| `/challenge logs admin ...` | Manage other players' log preferences | `deepcore.challenge.logs.admin` |
| `/challenge train` / `stop` | Enter or exit the training gym | `deepcore.challenge` |
| `/lobby` | Return to the DeepCore lobby from the training gym | `deepcore.challenge` |

Full subcommand usage: `/challenge <status|train|list|enable|disable|mode|component|end|stop|pause|resume|reset|resetworld|lobby|reload|logs>`

## Requirements

- Paper 1.21.10 (`api-version: "1.21"` in `plugin.yml`)
- Java 21
- `org.xerial:sqlite-jdbc` (used by `RunRecordsService` for run-records storage; shaded into the jar via the `com.gradleup.shadow` plugin, so no separate install is needed)

## Getting Started (Developers)

### Prerequisites

- Gradle (wrapper included, `gradlew`/`gradlew.bat`)
- Java 21
- Python 3 (for pre-commit hooks)

### Build

```
./gradlew clean build
```

Output artifact: `build/libs/DeepCore-<version>.jar`

### Install

Drop the built jar into your server's `plugins/` folder and restart.

## Configuration

Key sections in `config.yml`:

- `challenge` - enabled state, active mode, prep/countdown behavior, preview hologram settings, and the `components` map of mechanic toggles (keep_inventory, hardcore, health_refill, shared_inventory, shared_health, initial_half_heart, degrading_inventory) plus `degrading` interval/min-slots
- `records` - SQLite database file name for run records
- `prep` - countdown duration in seconds
- `reset` - world names used for limbo/lobby/nether reset and disco-world chance
- `logging` - console/chat log levels, prefix, and per-player log level overrides
- `training` - training gym world, spawn points, craft challenge ranges, and per-challenge (portal/craft/chest/bridge) regions and start locations

## CI/CD

Workflows in `.github/workflows/`:

- `ci.yml` - on PRs to `main`: runs tests with coverage gates (total line coverage >= 80%, changed-lines coverage >= 70% via diff-cover), uploads coverage to Codecov, and lints commit messages (commitlint, Angular-style conventional commits). On pushes to `main`: runs the full quality suite (format check via Spotless, typecheck/compile, Checkstyle lint, tests, coverage verification).
- `static.yml` - on pushes to `main`: generates Javadoc and publishes it to the `gh-pages` branch.

### Local Commit Enforcement

This repository enforces the same quality checks locally via pre-commit hooks:

- Formatting (Spotless)
- Typecheck/compile (main + test sources)
- Lint (Checkstyle)
- Commit message convention (Angular-style conventional commits)

Enable hooks once per clone:

```
pip install pre-commit
pre-commit install
pre-commit install --hook-type commit-msg
```

Run hooks manually:

```
pre-commit run --all-files
```

Conventional commit examples:

- feat: add end portal platform targeting
- fix: handle countdown cancellation when all players leave
- chore: update ci workflow gates

## Contributing

See [.github/pull_request_template.md](.github/pull_request_template.md) and the issue templates in [.github/ISSUE_TEMPLATE](.github/ISSUE_TEMPLATE). Commits must follow Angular-style conventional commit format (enforced by commitlint in CI and locally via pre-commit).

## License

MIT - see [LICENSE](LICENSE).

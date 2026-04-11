<img width="1156" height="430" alt="Capsule Art" src="https://github.com/user-attachments/assets/c338d277-2d2d-4b74-9a04-4c2dbd4b8f5b" />

<p align="center">A plugin for collaborative speedruns with toggleable mechanics.</p>

<p align="center">
    <a href="https://github.com/StoryTime-Productions/DeepCore/graphs/contributors">
        <img src="https://badgen.net/github/contributors/StoryTime-Productions/DeepCore?color=6f42c1" alt="Contributors">
    </a>
    <a href="https://github.com/StoryTime-Productions/DeepCore/network/members">
        <img src="https://badgen.net/github/forks/StoryTime-Productions/DeepCore?color=2ea44f" alt="Forks">
    </a>
    <a href="https://github.com/StoryTime-Productions/DeepCore/stargazers">
        <img src="https://badgen.net/github/stars/StoryTime-Productions/DeepCore?color=f59e0b" alt="Stargazers">
    </a>
    <a href="https://github.com/StoryTime-Productions/DeepCore/issues">
        <img src="https://badgen.net/github/open-issues/StoryTime-Productions/DeepCore?color=d73a49" alt="Issues">
    </a>
    <a href="https://github.com/StoryTime-Productions/DeepCore/blob/main/LICENSE">
        <img src="https://badgen.net/github/license/StoryTime-Productions/DeepCore?color=0ea5e9" alt="License">
    </a>
    <a href="https://codecov.io/gh/StoryTime-Productions/DeepCore">
        <img src="https://codecov.io/gh/StoryTime-Productions/DeepCore/branch/main/graph/badge.svg?color=9333ea" alt="Coverage">
    </a>
</p>

## What You Get

- Prep GUI for challenge setup before each run
- Presets plus per-mechanic toggles
- Shared and individual gameplay modifiers (health, inventory, hardcore, and more)
- Run records storage via SQLite
- World reset workflow between runs

## Prep Flow (Player Experience)

1. Every online player receives the DeepCore prep book.
2. Right-click the book to open the prep GUI.
3. Configure preset and mechanic toggles.
4. Each player marks themselves ready.
5. When everyone is ready, countdown starts and settings lock.
6. Run begins in the generated challenge world.

## Implemented Mechanic Toggles

- keep_inventory
- unlimited_deaths
- hardcore
- health_refill
- shared_inventory
- shared_health
- initial_half_heart
- degrading_inventory

## Command Reference

- /challenge status
- /challenge list
- /challenge enable
- /challenge disable
- /challenge mode <mode-key>
- /challenge component list
- /challenge component status
- /challenge component reset
- /challenge component <component-key> <on|off|toggle>

## Quick Start (Developers)

Prerequisites:

- Java 21
- Python 3 (for pre-commit)

Build and test:

    ./gradlew clean build

Output artifact:

- build/libs/DeepCore-<version>.jar

## Local Commit Enforcement

This repository enforces quality checks when you attempt a commit.

Checks on commit attempt:

- Formatting (Spotless)
- Typecheck/compile (main + test sources)
- Lint (Checkstyle)
- Commit message convention (Angular-style conventional commits)

Enable hooks once per clone:

    pip install pre-commit
    pre-commit install
    pre-commit install --hook-type commit-msg

Run hooks manually:

    pre-commit run --all-files

Conventional commit examples:

- feat: add end portal platform targeting
- fix: handle countdown cancellation when all players leave
- chore: update ci workflow gates

## CI/CD Rules

PRs to main:

- Runs tests
- Enforces total line coverage >= 80%
- Enforces changed-lines coverage >= 70%

Pushes to main:

- Runs full quality suite (format check, typecheck, lint, tests, coverage verification)

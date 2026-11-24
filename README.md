# AI Smart Recipe and Meal Planner


- Stage 2 report: [`EECS3311 Stage 2 Implementation Report.pdf`](EECS3311%20Stage%202%20Implementation%20Report.pdf)
- Demo video: https://youtu.be/bQ--vuo2Hz0
- UML diagrams: in the Stage 2 report, Section 3.6
- Standalone agent: [`Agent.jar`](#standalone-agent-agentjar)

## Features

| ID | Feature | AI used |
|---|---|---|
| F01 | Meal Calendar (month view, meal details, pantry check) | No |
| F02 | Shopping Checklist (several lists, staples, Finish Shopping fills the pantry) | Checks new item names |
| F03 | Shopping List Generation from recipes | Claude groups and tidies the list |
| F04 | Pantry Inventory (amounts, staples, out of stock) | Checks new ingredient names |
| F05 | Macro Generator (USDA nutrition, incl. sodium and fibre) | Claude picks the USDA food |
| F06 | Profile Configuration (diet, allergies, favourite categories, daily goals) | No |
| F07 | Recipe Recommendations (agent with tools, can write new recipes) | Yes |
| F08 | Recipe Library | Claude picks the USDA food |
| F09 | Favourite Recipes | No |
| F10 | Nutrition Summary (day and week vs goals) | Claude writes the review |
| F11 | AI Diet Guide (new in Stage 2) | Yes |


## Prerequisites
- Java 21 or newer (JDK)
- Maven 3.9 or newer
- Internet connection for Claude and USDA (optional; the app runs offline with fallbacks)
- An Anthropic API key (optional) and a USDA FoodData Central key (optional, `DEMO_KEY` is used if none is set)

## Setup

1. Clone the repository:
   ```
   git clone https://github.com/projectbyrome/EECS3311-Course-Project.git
   cd EECS3311-Course-Project
   ```
2. Create your local settings file from the example:
   ```
   cp .env.example .env        (Windows: copy .env.example .env)
   ```
3. Open `.env` and put in your own keys. `.env` is in `.gitignore` and must never be committed.

Settings (in `.env` or as environment variables; environment variables win):

| Setting | Meaning |
|---|---|
| `ANTHROPIC_API_KEY` | Your Anthropic API key. Leave empty to run without Claude. |
| `MEALPLANNER_MODEL` | Claude model id, e.g. `claude-haiku-5-5` |
| `USDA_API_KEY` | USDA FoodData Central key (default `DEMO_KEY`, rate limited) |
| `MEALPLANNER_DB` | Optional database path (default `~/.mealplanner/mealplanner.db`) |

## Run

GUI (window):
```
mvn -q compile javafx:run
```

CLI (command line):
```
mvn -q compile exec:java "-Dexec.args=--cli"
```
Type `help` in the CLI to see every command, and `exit` to quit.

## CLI examples

```
recipe list
calendar add --date 2026-11-20 --slot DINNER --recipe 3 --servings 2
calendar show --date 2026-11-20
pantry add eggs 12 PIECE
pantry staple salt
checklist new --name "Weekly shop"
shopping generate --recipes 3x2,5x1
macros ingredient --items banana:120:G,eggs:2:PIECE
profile set --allergies peanut --categories Soup,Spicy
recommend any 30 --ask something Japanese
recipe add --name Tomato_Pasta --servings 2 --ingredients pasta:200:g,tomato:3:piece
fav toggle 3
summary day 2026-11-20
chat what is the Mediterranean diet
```

## Standalone agent (Agent.jar)

`Agent.jar` runs the meal planner's AI agent from a terminal, with no window. You write the task in a text file and the agent runs it.

Build it once (Maven puts it in `target/Agent.jar`, with every library inside):
```
mvn -q package
```

Run it:
```
java -jar target/Agent.jar -LLMAPI claude -input examples/task.txt
```

The API key is never an option on the command line. It is read from `ANTHROPIC_API_KEY`, as an environment variable or from the `.env` file in the folder you run the command from (see Setup).

### Options

| Option | Meaning |
|---|---|
| `-LLMAPI <claude\|none>` | The LLM to use. `claude` (default) calls the Anthropic Messages API with your `ANTHROPIC_API_KEY`. `none` runs offline with the app's fallbacks (no API key needed). `-LLMAPI` with no value means `claude`. |
| `-input <file>` | The task file to run. Required. |
| `-agent <name>` | `auto` (default), `recommend`, `guide` or `commands`. See below. |
| `-model <id>` | Claude model id, e.g. `claude-haiku-5-5`. Default: `MEALPLANNER_MODEL`. |
| `-db <file>` | Database file. Default: `MEALPLANNER_DB` or `~/.mealplanner/mealplanner.db`, the same data as the app. |
| `-approve <n>` | `recommend` only: add recommendation number `n` to the recipe library. |
| `-output <file>` | Also write the result to this file. |
| `-help` | Show the options. |

Options can also be written with two dashes (`--input`).

### Agents

| Agent | What it does with the task |
|---|---|
| `recommend` | The recipe recommendation agent (F07). Claude uses the `get_profile`, `search_recipe_library` and `search_recipe_catalog` tools in a loop, then returns up to 3 recipes with reasons. It can write a new recipe when the catalogue has too few. Every pick is re-checked against your allergies, diet and filters. |
| `guide` | The AI Diet Guide (F11). The task is one question about a diet; the answer has the diet's sections and a sample day. |
| `commands` | Each line of the file is a CLI command (the same ones as in the CLI above), run in order. |
| `auto` | Picks one for you: `commands` if every line starts with a CLI command, `recommend` if the task talks about recipes or meals (recipe, cook, dinner, lunch, breakfast, snack, meal, dish, suggest...), otherwise `guide`. |

### Task file

Plain text. Lines starting with `#` are comments. Optional `key: value` lines at the top set the agent and the filters, and the rest is the task in your own words:

| Header | Example | Meaning |
|---|---|---|
| `agent:` | `recommend` | Same as `-agent` (the `-agent` option wins) |
| `category:` | `Curry`, `Rice Dishes`, `any` | Recipe category |
| `minutes:` | `30` | Maximum cooking time |
| `goal:` | `high-protein`, `low-calorie`, `low-carb`, `profile` | Nutrition goal |

`examples/task.txt`:
```
agent: recommend
category: any
minutes: 30
goal: high-protein
Something with chicken that I can make for dinner on a weeknight.
```

### Working example

With Claude (needs `ANTHROPIC_API_KEY`):
```
java -jar target/Agent.jar -LLMAPI claude -input examples/task.txt
```
The agent prints the task it understood, then up to 3 recipe cards with Claude's reason for each. Add one to your library with `-approve <number>`.

Without a key, the same task runs offline and shows the first catalogue recipes that match the filters:
```
$ java -jar target/Agent.jar -LLMAPI none -input examples/task.txt
Agent: recommend | LLM: none | task: examples/task.txt

Task: any category, at most 30 min, daily goal min 120 g protein, request "Something with chicken that I can make for dinner on a weeknight."

Claude is unavailable, so these are the first recipes that match your filters.
1. Chicken Parmesan Pasta  [catalogue #2]  30 min, 738 kcal, 62 g protein per serving
   Pan-Fried, Boiled, Pasta, Savory, Cheesy, Rich, Poultry, Pasta, Cheese

2. Garlic Shrimp Linguine  [catalogue #3]  20 min, 554 kcal, 36 g protein per serving
   Pan-Fried, Boiled, Pasta, Garlicky, Buttery, Citrusy, Shellfish, Seafood, Pasta

3. Chicken Burrito Bowl  [catalogue #6]  25 min, 616 kcal, 41 g protein per serving
   Pan-Fried, Bowls, Rice Dishes, Spicy, Savory, Citrusy, Poultry, Rice, Beans

To add one to your library, run again with -approve <number>.
```

More examples:
```
java -jar target/Agent.jar -LLMAPI claude -input examples/task.txt -approve 1
java -jar target/Agent.jar -LLMAPI claude -input examples/guide-task.txt -output answer.txt
java -jar target/Agent.jar -LLMAPI none -input examples/commands-task.txt
java -jar target/Agent.jar -LLMAPI claude -input examples/all-features.txt
```
Offline, the Diet Guide says that it needs Claude.

Exit codes: `0` done, `1` the task could not be completed (for example no recipe found, or Claude unreachable for the Diet Guide), `2` wrong options, missing task file or missing API key.

## Project structure

`src/main/java/mealplanner/`: `domain`, `model`, `events`, `data` (SQLite), `service`, `external` (USDA), `agent` (agent loop, `llm`, `tools`, `workflows`), `app` (AppFacade), `ui` (JavaFX), `cli`, `main`.
`src/test/java/`: check programs used during development.

## Starting fresh

Delete `~/.mealplanner/mealplanner.db` to reset all data. It is rebuilt with the sample recipes on the next start.

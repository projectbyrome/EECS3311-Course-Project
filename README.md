# AI Smart Recipe and Meal Planner


- Stage 2 report: [`stage2-report.pdf`](stage2-report.pdf)
- Demo video: https://youtu.be/bQ--vuo2Hz0
- UML diagrams: [`docs/diagrams`](docs/diagrams)

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

## Project structure

`src/main/java/mealplanner/`: `domain`, `model`, `events`, `data` (SQLite), `service`, `external` (USDA), `agent` (agent loop, `llm`, `tools`, `workflows`), `app` (AppFacade), `ui` (JavaFX), `cli`, `main`.
`src/test/java/`: check programs used during development.

## Starting fresh

Delete `~/.mealplanner/mealplanner.db` to reset all data. It is rebuilt with the sample recipes on the next start.
